package com.bsn.backend.social.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/** DB-native "user embedding" (§2.2 / §4.3) — decayed tag & creator affinities. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_interest_profile")
public class UserInterestProfile {

    @Id
    private String userId;

    private Map<String, Double> tags;      // tag -> affinity weight
    private Map<String, Double> creators;  // creatorId -> affinity weight (top ~50)
    private String city;
    private Instant lastRecomputedAt;
}
