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
import java.util.Map;

/** The heart of MEHNAT — every point traces back to one of these (§2.2). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "verifications")
@CompoundIndex(name = "user_day", def = "{'userId':1,'localDate':1,'status':1}")
@CompoundIndex(name = "review_queue", def = "{'status':1,'createdAt':1}")
public class Verification {

    @Id
    private String id;

    private String userId;

    @Indexed(unique = true)
    private String postId;

    private String method;             // AUTO | MANUAL_REVIEW | PEER
    private Map<String, Boolean> checks;  // liveness, freshRecording, durationOk...
    private String activityLabel;      // "gym", "running" — user-declared in Phase 1
    private String status;             // PENDING | VERIFIED | REJECTED
    private String reviewerId;
    private String rejectReason;

    private int effortSeconds;
    private int pointsAwarded;
    private double multiplierApplied;

    private String localDate;          // "2026-07-04" in user's tz — streak day-bucketing
    private String tz;

    private Instant createdAt;
    private Instant decidedAt;
}
