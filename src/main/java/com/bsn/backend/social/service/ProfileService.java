package com.bsn.backend.social.service;

import com.bsn.backend.exception.ResourceNotFoundException;
import com.bsn.backend.social.common.ConflictException;
import com.bsn.backend.social.model.Follow;
import com.bsn.backend.social.model.SocialProfile;
import com.bsn.backend.social.model.Streak;
import com.bsn.backend.social.repo.FollowRepository;
import com.bsn.backend.social.repo.SocialProfileRepository;
import com.bsn.backend.social.repo.StreakRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final SocialProfileRepository profiles;
    private final FollowRepository follows;
    private final StreakRepository streaks;
    private final NotificationService notifications;
    private final MongoTemplate mongo;

    /* ── profiles ─────────────────────────────────────────── */

    public SocialProfile me(String userId) {
        return byUserId(userId);
    }

    public SocialProfile byUserId(String userId) {
        return profiles.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("profile not found"));
    }

    public SocialProfile byHandle(String handle) {
        return profiles.findByHandle(handle.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("profile not found: " + handle));
    }

    public SocialProfile updateMe(String userId, Map<String, Object> patch) {
        SocialProfile p = byUserId(userId);
        if (patch.containsKey("displayName")) {
            p.setDisplayName((String) patch.get("displayName"));
        }
        if (patch.containsKey("bio")) {
            p.setBio((String) patch.get("bio"));
        }
        if (patch.containsKey("avatarUrl")) {
            p.setAvatarUrl((String) patch.get("avatarUrl"));
        }
        if (patch.containsKey("city")) {
            p.setCity((String) patch.get("city"));
        }
        if (patch.containsKey("tz")) {
            p.setTz((String) patch.get("tz"));
        }
        if (patch.containsKey("privateAccount")) {
            p.setPrivateAccount(Boolean.TRUE.equals(patch.get("privateAccount")));
        }
        p.setUpdatedAt(Instant.now());
        return profiles.save(p);
    }

    /* ── follow graph ─────────────────────────────────────── */

    public void follow(String followerId, String followeeId) {
        if (followerId.equals(followeeId)) {
            throw new IllegalArgumentException("cannot follow yourself");
        }
        SocialProfile target = byUserId(followeeId); // 404 if missing
        try {
            follows.save(Follow.builder()
                    .followerId(followerId).followeeId(followeeId)
                    .state("ACTIVE").createdAt(Instant.now())
                    .build());
        } catch (DuplicateKeyException e) {
            throw new ConflictException("already following");
        }
        incStat(followerId, "stats.following", 1);
        incStat(followeeId, "stats.followers", 1);
        SocialProfile follower = byUserId(followerId);
        notifications.push(followeeId, "FOLLOW", followerId, "user", followerId,
                "@" + follower.getHandle() + " started following you");
    }

    public void unfollow(String followerId, String followeeId) {
        if (!follows.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            return; // idempotent
        }
        follows.deleteByFollowerIdAndFolloweeId(followerId, followeeId);
        incStat(followerId, "stats.following", -1);
        incStat(followeeId, "stats.followers", -1);
    }

    public List<Map<String, Object>> followers(String userId, int page, int size) {
        return follows.findByFolloweeId(userId, PageRequest.of(page, Math.min(size, 50)))
                .stream().map(f -> brief(f.getFollowerId())).toList();
    }

    public List<Map<String, Object>> following(String userId, int page, int size) {
        return follows.findByFollowerId(userId, PageRequest.of(page, Math.min(size, 50)))
                .stream().map(f -> brief(f.getFolloweeId())).toList();
    }

    public List<String> followeeIds(String userId) {
        return follows.findByFollowerId(userId).stream().map(Follow::getFolloweeId).toList();
    }

    public Map<String, Object> brief(String userId) {
        return profiles.findById(userId).map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("userId", p.getUserId());
            m.put("handle", p.getHandle());
            m.put("displayName", p.getDisplayName());
            m.put("avatarUrl", p.getAvatarUrl());
            m.put("verifiedHuman", p.isVerifiedHuman());
            m.put("tier", p.getRank() == null ? null : p.getRank().getTier());
            return m;
        }).orElseGet(HashMap::new);
    }

    /* ── KYC (Phase 1 stub — instant approval, swap for real provider) ── */

    public Map<String, Object> submitKyc(String userId, String fullName, String idNumber) {
        if (fullName == null || idNumber == null || idNumber.length() < 6) {
            throw new IllegalArgumentException("fullName and idNumber required");
        }
        mongo.updateFirst(new Query(Criteria.where("_id").is(userId)),
                new Update().set("kycStatus", "VERIFIED").set("updatedAt", Instant.now()),
                SocialProfile.class);
        return Map.of("kycStatus", "VERIFIED",
                "note", "Phase 1 stub: auto-approved. Wire a KYC provider before real-world redemptions.");
    }

    /* ── flex card (share profile · QR verify link) ────────── */

    public Map<String, Object> flexCard(String handle) {
        SocialProfile p = byHandle(handle);
        Streak streak = streaks.findById(p.getUserId()).orElse(null);
        Map<String, Object> card = new HashMap<>();
        card.put("handle", p.getHandle());
        card.put("displayName", p.getDisplayName());
        card.put("city", p.getCity());
        card.put("memberSince", p.getMemberSince());
        card.put("verifiedHuman", p.isVerifiedHuman());
        card.put("tier", p.getRank() == null ? null : p.getRank().getTier());
        card.put("rr", p.getRank() == null ? 0 : p.getRank().getRr());
        card.put("stats", p.getStats());
        card.put("currentStreak", streak == null ? 0 : streak.getCurrent());
        card.put("shareUrl", "https://mehnat.app/r/" + p.getShareSlug());
        card.put("qrVerifyUrl", "https://mehnat.app/r/" + p.getShareSlug() + "?verify=1");
        return card;
    }

    /* ── helpers ──────────────────────────────────────────── */

    public void incStat(String userId, String statPath, long delta) {
        mongo.updateFirst(new Query(Criteria.where("_id").is(userId)),
                new Update().inc(statPath, delta), SocialProfile.class);
    }

    /** "✔ VERIFIED HUMAN" badge — flipped on first successful verification. */
    public void markVerifiedHuman(String userId) {
        mongo.updateFirst(new Query(Criteria.where("_id").is(userId)),
                new Update().set("verifiedHuman", true), SocialProfile.class);
    }
}
