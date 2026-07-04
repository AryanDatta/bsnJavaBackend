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

/** Raw signal firehose — Phase 1 stand-in for Kafka (§2.2). Feeds interest profiles + velocity. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "engagement_events")
@CompoundIndex(name = "user_time", def = "{'userId':1,'createdAt':-1}")
@CompoundIndex(name = "post_type", def = "{'postId':1,'type':1}")
public class EngagementEvent {

    @Id
    private String id;

    private String userId;
    private String postId;
    private String authorId;
    private String type;        // VIEW | COMPLETE_VIEW | LIKE | COMMENT | SHARE | JOIN_CLICK | SKIP | REPORT
    private long dwellMs;
    private List<String> tags;
    private String source;      // FEED | EXPLORE | PROFILE | CHALLENGE

    @Indexed(expireAfter = "90d")
    private Instant createdAt;
}
