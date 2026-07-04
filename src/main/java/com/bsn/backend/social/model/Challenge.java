package com.bsn.backend.social.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/** Sponsored/community challenge — payout back-loaded: "a finisher's game" (§2.2). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "challenges")
@CompoundIndex(name = "status_city", def = "{'status':1,'city':1,'startAt':-1}")
public class Challenge {

    @Id
    private String id;

    @Indexed(unique = true)
    private String slug;            // "fitzone-30"

    private String sponsor;
    private String title;
    private String description;
    private String city;            // null = everywhere

    private int durationDays;
    private Instant startAt;
    private Instant endAt;
    private int maxPoints;

    private List<CurveSegment> payoutCurve;
    private Rules rules;
    private FriendBonus friendBonus;
    private Stats stats;

    private String status;          // UPCOMING | ACTIVE | ENDED

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurveSegment {
        private int fromDay;
        private int toDay;
        private int ptsPerDay;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Rules {
        private int videosPerDay;
        private int freezesIncluded;
        private int quitPenaltyPts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FriendBonus {
        private int threshold;
        private int bonusPts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stats {
        private long joined;
        private long finished;
        private double finishRate;
    }
}
