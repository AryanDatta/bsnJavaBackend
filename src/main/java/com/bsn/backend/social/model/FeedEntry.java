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

/** Precomputed home-feed row — fan-out-on-write target (§3.1). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "feed_entries")
@CompoundIndex(name = "owner_time", def = "{'ownerId':1,'createdAt':-1}")
@CompoundIndex(name = "uniq_owner_post", def = "{'ownerId':1,'postId':1}", unique = true)
public class FeedEntry {

    @Id
    private String id;

    private String ownerId;     // whose feed
    @Indexed
    private String postId;      // for delete fan-out
    private String authorId;
    private double baseScore;   // static part (mehnat boost); viewer terms applied at read
    private String reason;      // FOLLOWING | SQUAD | CHALLENGE | RECO | TRENDING

    @Indexed(expireAfter = "14d")
    private Instant createdAt;
}
