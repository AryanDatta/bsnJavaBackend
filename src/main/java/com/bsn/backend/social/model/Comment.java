package com.bsn.backend.social.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "comments")
@CompoundIndex(name = "post_time", def = "{'postId':1,'createdAt':-1}")
public class Comment {

    @Id
    private String id;

    private String postId;
    private String authorId;
    private String authorHandle;
    private String parentId;     // null = top-level, else reply
    private String text;
    private long likes;
    private String status;       // LIVE | REMOVED
    private Instant createdAt;
}
