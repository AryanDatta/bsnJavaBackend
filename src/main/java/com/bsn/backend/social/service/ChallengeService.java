package com.bsn.backend.social.service;

import com.bsn.backend.exception.ResourceNotFoundException;
import com.bsn.backend.social.common.ConflictException;
import com.bsn.backend.social.model.Challenge;
import com.bsn.backend.social.model.ChallengeParticipant;
import com.bsn.backend.social.model.Post;
import com.bsn.backend.social.repo.ChallengeParticipantRepository;
import com.bsn.backend.social.repo.ChallengeRepository;
import com.bsn.backend.social.repo.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Challenge engine (§5.4). Payouts vest at challenge end ("+180 maturing in 4 days")
 * so quitters forfeit unvested points — 77% of the value is in the final stretch.
 */
@Service
@RequiredArgsConstructor
public class ChallengeService {

    private final ChallengeRepository challenges;
    private final ChallengeParticipantRepository participants;
    private final PostRepository posts;
    private final ProfileService profileService;
    private final WalletService wallet;
    private final StreakService streakService;
    private final RankService rankService;
    private final NotificationService notifications;
    private final MongoTemplate mongo;

    /* ── discovery ────────────────────────────────────────── */

    public List<Challenge> list(String status, String city, int page, int size) {
        PageRequest pr = PageRequest.of(page, Math.min(size, 50));
        String st = status == null ? "ACTIVE" : status;
        return city == null
                ? challenges.findByStatusOrderByStartAtDesc(st, pr)
                : challenges.findByStatusAndCityOrderByStartAtDesc(st, city, pr);
    }

    public Challenge bySlug(String slug) {
        return challenges.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("challenge not found: " + slug));
    }

    /* ── join / quit ──────────────────────────────────────── */

    public ChallengeParticipant join(String userId, String slug) {
        Challenge challenge = bySlug(slug);
        if (!"ACTIVE".equals(challenge.getStatus()) && !"UPCOMING".equals(challenge.getStatus())) {
            throw new ConflictException("challenge is not open");
        }
        if (participants.findByChallengeIdAndUserId(challenge.getId(), userId).isPresent()) {
            throw new ConflictException("already joined");
        }

        // friend bonus: followees already in ("Arjun + 2 friends are in")
        List<String> followees = profileService.followeeIds(userId);
        List<String> friendsIn = new ArrayList<>();
        for (String friendId : followees) {
            participants.findByChallengeIdAndUserId(challenge.getId(), friendId)
                    .ifPresent(p -> friendsIn.add(friendId));
        }

        ChallengeParticipant participant = participants.save(ChallengeParticipant.builder()
                .challengeId(challenge.getId()).challengeSlug(slug).userId(userId)
                .joinedAt(Instant.now())
                .day(0).verifiedDays(new ArrayList<>())
                .freezesLeft(challenge.getRules() == null ? 0 : challenge.getRules().getFreezesIncluded())
                .friendsJoined(friendsIn).friendBonusPaid(false)
                .state("ACTIVE").pointsEarned(0)
                .build());

        // streak freezes included with the challenge
        if (challenge.getRules() != null && challenge.getRules().getFreezesIncluded() > 0) {
            streakService.addFreezes(userId, challenge.getRules().getFreezesIncluded());
        }

        // friend bonus (threshold met at join time)
        if (challenge.getFriendBonus() != null
                && friendsIn.size() >= challenge.getFriendBonus().getThreshold()) {
            wallet.append(userId, challenge.getFriendBonus().getBonusPts(), "EARN_BONUS",
                    "challenge", challenge.getId(), "friendbonus:" + challenge.getId() + ":" + userId, null);
            participant.setFriendBonusPaid(true);
            participants.save(participant);
        }

        mongo.updateFirst(new Query(Criteria.where("_id").is(challenge.getId())),
                new Update().inc("stats.joined", 1), Challenge.class);
        return participant;
    }

    public Map<String, Object> quit(String userId, String slug) {
        Challenge challenge = bySlug(slug);
        ChallengeParticipant participant = activeParticipant(challenge.getId(), userId);
        participant.setState("QUIT");
        participants.save(participant);

        long penalty = challenge.getRules() == null ? 0 : challenge.getRules().getQuitPenaltyPts();
        long forfeited = forfeitUnmatured(userId, challenge.getId());
        if (penalty > 0) {
            long available = wallet.availableBalance(userId);
            long applied = Math.min(penalty, Math.max(0, available));
            if (applied > 0) {
                wallet.append(userId, -applied, "PENALTY_QUIT", "challenge", challenge.getId(),
                        "quit:" + challenge.getId() + ":" + userId, null);
            }
        }
        return Map.of("state", "QUIT", "penaltyPts", penalty, "forfeitedMaturingPts", forfeited);
    }

    /* ── progress & leaderboard ───────────────────────────── */

    public Map<String, Object> myProgress(String userId, String slug) {
        Challenge challenge = bySlug(slug);
        ChallengeParticipant p = participants.findByChallengeIdAndUserId(challenge.getId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("not a participant"));
        Map<String, Object> out = new HashMap<>();
        out.put("participant", p);
        out.put("currentDay", currentDay(challenge));
        out.put("durationDays", challenge.getDurationDays());
        out.put("payoutCurve", challenge.getPayoutCurve());
        return out;
    }

    public List<Map<String, Object>> leaderboard(String slug, int page, int size) {
        Challenge challenge = bySlug(slug);
        List<ChallengeParticipant> rows = participants.findByChallengeIdOrderByPointsEarnedDesc(
                challenge.getId(), PageRequest.of(page, Math.min(size, 50)));
        List<Map<String, Object>> out = new ArrayList<>();
        int base = page * Math.min(size, 50);
        for (int i = 0; i < rows.size(); i++) {
            ChallengeParticipant p = rows.get(i);
            Map<String, Object> m = profileService.brief(p.getUserId());
            m.put("rank", base + i + 1);
            m.put("pts", p.getPointsEarned());
            m.put("day", p.getDay());
            m.put("state", p.getState());
            out.add(m);
        }
        return out;
    }

    /** Day-scrubber feed (D1 → D30) of verified challenge posts (§6.8). */
    public List<Post> feed(String slug, Integer day, int page, int size) {
        Challenge challenge = bySlug(slug);
        Query q = new Query(Criteria.where("challengeId").is(challenge.getId())
                .and("status").is("LIVE"));
        if (day != null) {
            q.addCriteria(Criteria.where("verification.day").is(day));
        }
        q.with(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "velocity", "createdAt"))
                .skip((long) page * Math.min(size, 50)).limit(Math.min(size, 50));
        return mongo.find(q, Post.class);
    }

    /* ── verification hook (golden path §7.1) ─────────────── */

    /** Credits today's challenge day; returns the day number for the post badge, or null. */
    public Integer onVerified(String userId, Post post, String localDate) {
        if (post.getChallengeId() == null) {
            return null;
        }
        Challenge challenge = challenges.findById(post.getChallengeId()).orElse(null);
        if (challenge == null || !"ACTIVE".equals(challenge.getStatus())) {
            return null;
        }
        ChallengeParticipant p = participants.findByChallengeIdAndUserId(challenge.getId(), userId)
                .filter(x -> "ACTIVE".equals(x.getState())).orElse(null);
        if (p == null) {
            return null;
        }
        if (localDate.equals(p.getLastCreditLocalDate())) {
            return p.getDay(); // one credit per local day
        }

        int day = currentDay(challenge);
        if (day < 1 || day > challenge.getDurationDays()) {
            return null;
        }

        long pts = ptsForDay(challenge, day);
        // payouts vest at challenge end — quitters forfeit (§5.4)
        wallet.append(userId, pts, "EARN_CHALLENGE", "challenge", challenge.getId(),
                "chday:" + challenge.getId() + ":" + userId + ":" + day, challenge.getEndAt());

        p.setDay(Math.max(p.getDay(), day));
        if (p.getVerifiedDays() == null) {
            p.setVerifiedDays(new ArrayList<>());
        }
        if (!p.getVerifiedDays().contains(day)) {
            p.getVerifiedDays().add(day);
        }
        p.setLastCreditLocalDate(localDate);
        p.setPointsEarned(p.getPointsEarned() + pts);

        // finish check: all days covered (verified + freezes) at the final day
        if (day >= challenge.getDurationDays()
                && p.getVerifiedDays().size() + p.getFreezesLeft() >= challenge.getDurationDays()) {
            p.setState("FINISHED");
            p.setFinishedAt(Instant.now());
            mongo.updateFirst(new Query(Criteria.where("_id").is(challenge.getId())),
                    new Update().inc("stats.finished", 1), Challenge.class);
            profileService.incStat(userId, "stats.challengesDone", 1);
            rankService.creditChallengeFinish(userId);
            notifications.push(userId, "CHALLENGE", null, "challenge", challenge.getId(),
                    "You finished " + challenge.getTitle() + " — a finisher's game, and you finished it.");
        }
        participants.save(p);
        return day;
    }

    /* ── helpers ──────────────────────────────────────────── */

    private ChallengeParticipant activeParticipant(String challengeId, String userId) {
        return participants.findByChallengeIdAndUserId(challengeId, userId)
                .filter(p -> "ACTIVE".equals(p.getState()))
                .orElseThrow(() -> new ConflictException("no active participation"));
    }

    private int currentDay(Challenge challenge) {
        if (challenge.getStartAt() == null) {
            return 0;
        }
        long days = Duration.between(challenge.getStartAt(), Instant.now()).toDays();
        return (int) days + 1;
    }

    private long ptsForDay(Challenge challenge, int day) {
        if (challenge.getPayoutCurve() == null) {
            return 10;
        }
        return challenge.getPayoutCurve().stream()
                .filter(seg -> day >= seg.getFromDay() && day <= seg.getToDay())
                .mapToLong(Challenge.CurveSegment::getPtsPerDay).findFirst().orElse(10);
    }

    private long forfeitUnmatured(String userId, String challengeId) {
        // unvested challenge earnings are cancelled by a negative matured entry
        long forfeited = 0;
        var unmatured = mongo.find(new Query(Criteria.where("userId").is(userId)
                        .and("matured").is(false).and("refType").is("challenge").and("refId").is(challengeId)),
                com.bsn.backend.social.model.WalletEntry.class);
        for (var entry : unmatured) {
            forfeited += entry.getDelta();
            entry.setMatured(true); // stop it from vesting
            mongo.save(entry);
        }
        if (forfeited > 0) {
            wallet.append(userId, -forfeited, "PENALTY_QUIT", "challenge", challengeId,
                    "forfeit:" + challengeId + ":" + userId, null);
        }
        return forfeited;
    }
}
