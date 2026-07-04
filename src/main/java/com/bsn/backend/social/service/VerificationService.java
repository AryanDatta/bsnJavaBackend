package com.bsn.backend.social.service;

import com.bsn.backend.exception.ResourceNotFoundException;
import com.bsn.backend.social.common.ConflictException;
import com.bsn.backend.social.common.ForbiddenException;
import com.bsn.backend.social.model.Post;
import com.bsn.backend.social.model.SocialProfile;
import com.bsn.backend.social.model.Verification;
import com.bsn.backend.social.repo.PostRepository;
import com.bsn.backend.social.repo.VerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The golden path (§7.1): record → verify → points → streak → RR → fan-out.
 * "Every number on this page was earned on camera."
 *
 * Phase 1 checks are heuristic (capture token, duration); suspicious submissions
 * drop to a manual review queue. Phase 2 swaps in liveness/face-match models.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final int MIN_EFFORT_SECONDS = 60;
    private static final int MAX_EFFORT_SECONDS = 4 * 3600;

    private final VerificationRepository verifications;
    private final PostRepository posts;
    private final ProfileService profileService;
    private final MediaService mediaService;
    private final WalletService wallet;
    private final StreakService streakService;
    private final RankService rankService;
    private final ChallengeService challengeService;
    private final SquadService squadService;
    private final PostService postService;
    private final NotificationService notifications;

    /* ── submission ───────────────────────────────────────── */

    public Verification submit(String userId, String postId, String activityLabel,
                               int effortSeconds, String captureToken) {
        Post post = posts.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("post not found: " + postId));
        if (!post.getAuthorId().equals(userId)) {
            throw new ForbiddenException("not your post");
        }
        if (post.getVerification() == null || !"PENDING".equals(post.getVerification().getStatus())) {
            throw new ConflictException("post is not awaiting verification");
        }
        if (verifications.findByPostId(postId).isPresent()) {
            throw new ConflictException("verification already submitted for this post");
        }

        SocialProfile profile = profileService.byUserId(userId);
        String tz = profile.getTz() == null ? AuthService.DEFAULT_TZ : profile.getTz();
        String localDate = LocalDate.now(StreakService.zone(tz)).toString();

        // ONE verified video per local day counts (§2.2)
        if (verifications.existsByUserIdAndLocalDateAndStatus(userId, localDate, "VERIFIED")) {
            throw new ConflictException("already verified today — one verified video per day counts");
        }

        // Phase 1 checks
        Map<String, Boolean> checks = new HashMap<>();
        String rawKey = post.getMedia() == null ? null : post.getMedia().getRawKey();
        checks.put("freshRecording", rawKey != null
                && mediaService.captureTokenValid(userId, rawKey, captureToken));
        checks.put("durationOk", effortSeconds >= MIN_EFFORT_SECONDS && effortSeconds <= MAX_EFFORT_SECONDS);
        checks.put("hasVideo", post.getMedia() != null);
        boolean allPass = checks.values().stream().allMatch(Boolean::booleanValue);

        Verification verification = verifications.save(Verification.builder()
                .userId(userId).postId(postId)
                .method(allPass ? "AUTO" : "MANUAL_REVIEW")
                .checks(checks).activityLabel(activityLabel)
                .status("PENDING")
                .effortSeconds(effortSeconds)
                .localDate(localDate).tz(tz)
                .createdAt(Instant.now())
                .build());

        if (allPass) {
            return award(verification, post, profile);
        }
        log.info("verification {} sent to manual review, checks={}", verification.getId(), checks);
        return verification;
    }

    public Verification get(String userId, String verificationId) {
        Verification v = verifications.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("verification not found"));
        if (!v.getUserId().equals(userId)) {
            throw new ForbiddenException("not your verification");
        }
        return v;
    }

    /* ── award (the only path that moves points, via WalletService) ── */

    private Verification award(Verification verification, Post post, SocialProfile profile) {
        double multiplier = rankService.multiplierOf(verification.getUserId());
        int basePts = 10 + Math.min(15, verification.getEffortSeconds() / 240); // 10..25
        int pts = (int) Math.round(basePts * multiplier);
        String userId = verification.getUserId();

        // 1. ledger (idempotent — "verif:<id>")
        wallet.append(userId, pts, "EARN_VERIFIED", "verification", verification.getId(),
                "verif:" + verification.getId(), null);

        // 2. streak
        streakService.recordVerified(userId, verification.getLocalDate(), verification.getTz());

        // 3. rank RR + season pts
        rankService.award(userId, (int) Math.ceil(pts / 10.0), pts, "VERIFIED_VIDEO");

        // 4. challenge day credit (vesting payout)
        Integer challengeDay = challengeService.onVerified(userId, post, verification.getLocalDate());

        // 5. squad daily status
        squadService.onVerified(userId, verification.getLocalDate());

        // 6. mark verification + post, go LIVE, fan-out
        verification.setStatus("VERIFIED");
        verification.setPointsAwarded(pts);
        verification.setMultiplierApplied(multiplier);
        verification.setDecidedAt(Instant.now());
        verifications.save(verification);

        post.setVerification(Post.VerificationInfo.builder()
                .status("VERIFIED").verificationId(verification.getId())
                .verifiedAt(Instant.now()).day(challengeDay)
                .build());
        post.setStatus("LIVE");
        posts.save(post);
        postService.onPostLive(post);

        // 7. profile stats + verified-human badge
        profileService.incStat(userId, "stats.verifiedDays", 1);
        profileService.incStat(userId, "stats.verifiedEffortSeconds", verification.getEffortSeconds());
        if (!profile.isVerifiedHuman()) {
            profileService.markVerifiedHuman(userId);
        }

        notifications.push(userId, "POINTS", null, "verification", verification.getId(),
                "Video verified · +" + pts + " pts");
        return verification;
    }

    /* ── manual review queue (Phase 1 fallback, §6.6) ─────── */

    public List<Verification> reviewQueue(int page, int size) {
        return verifications.findByStatusOrderByCreatedAtAsc("PENDING",
                PageRequest.of(page, Math.min(size, 50)));
    }

    public Verification decide(String reviewerId, String verificationId, boolean approve, String reason) {
        Verification v = verifications.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("verification not found"));
        if (!"PENDING".equals(v.getStatus())) {
            throw new ConflictException("already decided");
        }
        v.setReviewerId(reviewerId);
        if (approve) {
            Post post = posts.findById(v.getPostId())
                    .orElseThrow(() -> new ResourceNotFoundException("post gone"));
            return award(v, post, profileService.byUserId(v.getUserId()));
        }
        v.setStatus("REJECTED");
        v.setRejectReason(reason == null ? "did not meet verification rules" : reason);
        v.setDecidedAt(Instant.now());
        verifications.save(v);
        posts.findById(v.getPostId()).ifPresent(p -> {
            p.setVerification(Post.VerificationInfo.builder().status("REJECTED").build());
            posts.save(p);
        });
        notifications.push(v.getUserId(), "POINTS", null, "verification", v.getId(),
                "Verification rejected: " + v.getRejectReason());
        return v;
    }
}
