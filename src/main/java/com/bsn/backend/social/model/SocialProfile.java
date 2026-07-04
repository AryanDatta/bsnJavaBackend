package com.bsn.backend.social.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Public profile + denormalized stats (ARCHITECTURE.md §2.2 user_profiles). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_profiles")
public class SocialProfile {

    @Id
    private String userId;

    private String handle;
    private String displayName;
    private String bio;
    private String avatarUrl;
    private String city;
    private String tz;              // IANA zone, default Asia/Kolkata
    private String memberSince;     // season label, e.g. "S1"
    private boolean verifiedHuman;  // set after first successful verification
    private boolean privateAccount;
    private String kycStatus;       // NONE | PENDING | VERIFIED
    private String shareSlug;       // mehnat.app/r/{shareSlug}

    private Stats stats;
    private RankInfo rank;

    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stats {
        private long followers;
        private long following;
        private long posts;
        private long verifiedDays;
        private long verifiedEffortSeconds;
        private long challengesDone;
        private int currentStreak;
        private int longestStreak;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RankInfo {
        private String tier;
        private int rr;
        private double multiplier;
        private String season;
        private Instant heldSince;
    }
}
