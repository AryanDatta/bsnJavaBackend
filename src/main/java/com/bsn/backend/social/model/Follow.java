package com.bsn.backend.social.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Follow-graph edge; counts denormalized on SocialProfile.stats. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "follows")
@CompoundIndex(name = "uniq_edge", def = "{'followerId':1,'followeeId':1}", unique = true)
public class Follow {

    @Id
    private String id;

    private String followerId;
    private String followeeId;
    private String state;      // ACTIVE | REQUESTED
    private Instant createdAt;
}
