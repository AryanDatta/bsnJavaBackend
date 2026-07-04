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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notifications")
@CompoundIndex(name = "user_read_time", def = "{'userId':1,'read':1,'createdAt':-1}")
public class Notification {

    @Id
    private String id;

    private String userId;
    private String type;      // NUDGE | STREAK_RISK | RANK_DECAY | FOLLOW | LIKE | COMMENT | CHALLENGE | SQUAD | POINTS
    private String actorId;
    private String refType;
    private String refId;
    private String text;
    private boolean read;

    @Indexed(expireAfter = "60d")
    private Instant createdAt;
}
