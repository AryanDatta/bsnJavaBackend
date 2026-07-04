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

/** 24h ephemeral content; TTL index on expiresAt. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stories")
@CompoundIndex(name = "author_time", def = "{'authorId':1,'createdAt':-1}")
public class Story {

    @Id
    private String id;

    private String authorId;
    private String authorHandle;
    private String authorAvatarUrl;

    private String url;
    private String thumbUrl;
    private int durationSec;
    private String type;            // VIDEO | IMAGE
    private boolean verifiedClip;   // cut from a verified reel → ✔ ring

    private Instant createdAt;

    @Indexed(expireAfter = "0s") // TTL: delete at expiresAt
    private Instant expiresAt;
}
