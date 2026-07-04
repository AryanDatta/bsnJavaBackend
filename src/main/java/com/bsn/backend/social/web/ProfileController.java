package com.bsn.backend.social.web;

import com.bsn.backend.social.common.SecurityUtil;
import com.bsn.backend.social.model.Post;
import com.bsn.backend.social.model.SeasonRank;
import com.bsn.backend.social.model.SocialProfile;
import com.bsn.backend.social.repo.ChallengeParticipantRepository;
import com.bsn.backend.social.service.PostService;
import com.bsn.backend.social.service.ProfileService;
import com.bsn.backend.social.service.RankService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "mehnat · users & graph", description = "profiles, follow graph, KYC, flex card (§6.2)")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final PostService postService;
    private final RankService rankService;
    private final ChallengeParticipantRepository challengeParticipants;

    public record KycRequest(String fullName, String idNumber) {
    }

    /* ── me ───────────────────────────────────────────────── */

    @Operation(summary = "my profile")
    @GetMapping("/me")
    public SocialProfile me() {
        return profileService.me(SecurityUtil.currentUserId());
    }

    @Operation(summary = "edit my profile (displayName, bio, avatarUrl, city, tz, privateAccount)")
    @PatchMapping("/me")
    public SocialProfile updateMe(@RequestBody Map<String, Object> patch) {
        return profileService.updateMe(SecurityUtil.currentUserId(), patch);
    }

    @Operation(summary = "one-time KYC before first real-world redemption (§6.2)")
    @PostMapping("/me/kyc")
    public Map<String, Object> kyc(@RequestBody KycRequest req) {
        return profileService.submitKyc(SecurityUtil.currentUserId(), req.fullName(), req.idNumber());
    }

    @Operation(summary = "my KYC status")
    @GetMapping("/me/kyc")
    public Map<String, Object> kycStatus() {
        return Map.of("kycStatus", profileService.me(SecurityUtil.currentUserId()).getKycStatus());
    }

    /* ── public profiles ──────────────────────────────────── */

    @Operation(summary = "public profile by handle — stats, rank, badges")
    @GetMapping("/{handle}")
    public SocialProfile byHandle(@PathVariable String handle) {
        return profileService.byHandle(handle);
    }

    @Operation(summary = "profile posts tab")
    @GetMapping("/{handle}/posts")
    public List<Post> posts(@PathVariable String handle,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size) {
        SocialProfile p = profileService.byHandle(handle);
        return postService.byAuthor(p.getUserId(), page, size);
    }

    @Operation(summary = "season shelf — seasons are a collection, not a reset")
    @GetMapping("/{handle}/seasons")
    public List<SeasonRank> seasons(@PathVariable String handle) {
        return rankService.shelf(profileService.byHandle(handle).getUserId());
    }

    @Operation(summary = "completed challenges tab")
    @GetMapping("/{handle}/completed-challenges")
    public List<Map<String, Object>> completedChallenges(@PathVariable String handle) {
        SocialProfile p = profileService.byHandle(handle);
        return challengeParticipants.findByUserIdAndState(p.getUserId(), "FINISHED").stream()
                .map(cp -> Map.<String, Object>of(
                        "challengeSlug", cp.getChallengeSlug(),
                        "pointsEarned", cp.getPointsEarned(),
                        "finishedAt", cp.getFinishedAt() == null ? "" : cp.getFinishedAt().toString()))
                .toList();
    }

    @Operation(summary = "flex card + QR verify link (mehnat.app/r/{slug})")
    @GetMapping("/{handle}/flex-card")
    public Map<String, Object> flexCard(@PathVariable String handle) {
        return profileService.flexCard(handle);
    }

    /* ── follow graph ─────────────────────────────────────── */

    @Operation(summary = "follow a user")
    @PostMapping("/{userId}/follow")
    public Map<String, String> follow(@PathVariable String userId) {
        profileService.follow(SecurityUtil.currentUserId(), userId);
        return Map.of("message", "following");
    }

    @Operation(summary = "unfollow a user")
    @DeleteMapping("/{userId}/follow")
    public Map<String, String> unfollow(@PathVariable String userId) {
        profileService.unfollow(SecurityUtil.currentUserId(), userId);
        return Map.of("message", "unfollowed");
    }

    @Operation(summary = "followers list")
    @GetMapping("/{userId}/followers")
    public List<Map<String, Object>> followers(@PathVariable String userId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return profileService.followers(userId, page, size);
    }

    @Operation(summary = "following list")
    @GetMapping("/{userId}/following")
    public List<Map<String, Object>> following(@PathVariable String userId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return profileService.following(userId, page, size);
    }
}
