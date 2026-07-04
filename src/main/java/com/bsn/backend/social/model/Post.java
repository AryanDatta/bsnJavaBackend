package com.bsn.backend.social.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/** Reels & posts. Author fields denormalized for join-free feed rendering. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "posts")
@CompoundIndex(name = "author_time", def = "{'authorId':1,'createdAt':-1}")
@CompoundIndex(name = "challenge_time", def = "{'challengeId':1,'createdAt':-1}")
@CompoundIndex(name = "tags_time", def = "{'tags':1,'createdAt':-1}")
@CompoundIndex(name = "velocity", def = "{'velocity':-1,'createdAt':-1}")
public class Post {

    @Id
    private String id;

    private String authorId;
    private String authorHandle;
    private String authorAvatarUrl;
    private String authorCity;

    private String type;            // REEL | PHOTO
    private String caption;
    private List<String> tags;      // lowercase, e.g. ["gym","challenge:fitzone-30"]

    private Media media;
    private VerificationInfo verification;   // null for casual posts

    private String challengeId;
    private String squadId;
    private String visibility;      // PUBLIC | FOLLOWERS

    private Counts counts;
    private double velocity;        // engagement velocity, refreshed by job (§3.2)
    private Instant velocityUpdatedAt;

    private String status;          // LIVE | PROCESSING | REMOVED
    private Instant createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Media {
        private String rawKey;
        private String hlsUrl;
        private String thumbUrl;
        private int durationSec;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerificationInfo {
        private String status;          // PENDING | VERIFIED | REJECTED
        private String verificationId;
        private Instant verifiedAt;
        private Integer day;            // "Day 27" badge (challenge day)
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Counts {
        private long likes;
        private long comments;
        private long views;
        private long shares;
    }
}
