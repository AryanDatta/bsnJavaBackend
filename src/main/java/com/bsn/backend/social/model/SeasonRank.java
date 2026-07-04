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

/** One row per user per season — the "season shelf" (§2.2). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "season_ranks")
@CompoundIndex(name = "uniq_user_season", def = "{'userId':1,'seasonId':1}", unique = true)
@CompoundIndex(name = "season_rr", def = "{'seasonId':1,'rr':-1}")
@CompoundIndex(name = "city_lb", def = "{'seasonId':1,'city':1,'seasonPts':-1}")
public class SeasonRank {

    @Id
    private String id;

    private String userId;
    private String seasonId;
    private String city;            // denormalized for city leaderboards

    private String tier;            // Tier enum name
    private int rr;                 // cumulative season RR
    private long seasonPts;         // points earned this season (leaderboard score)

    private String peakTier;
    private Instant heldSince;
    private Instant lastEarnAt;
    private boolean decayActive;

    private int challengesFinished;
    private List<RrEvent> history;  // capped at 100

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RrEvent {
        private Instant at;
        private int delta;
        private String reason;      // VERIFIED_VIDEO | DECAY | CHALLENGE_FINISH
    }
}
