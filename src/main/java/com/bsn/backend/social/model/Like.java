package com.bsn.backend.social.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** One doc per like — idempotent like/unlike + "liked by you" lookups. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "likes")
@CompoundIndex(name = "uniq_like", def = "{'subjectType':1,'subjectId':1,'userId':1}", unique = true)
public class Like {

    @Id
    private String id;

    private String subjectType;   // POST | COMMENT | STORY
    private String subjectId;
    private String userId;
    private Instant createdAt;
}
