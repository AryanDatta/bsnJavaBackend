package com.bsn.backend.social.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Upload session for direct-to-storage uploads (§6.3). captureToken proves in-app camera. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "media_uploads")
public class MediaUpload {

    @Id
    private String id;              // uploadId (UUID)

    private String userId;
    private String captureToken;    // HMAC(uploadId:userId) — required for verifiable reels
    private String status;          // PENDING | UPLOADED | CONSUMED
    private Integer durationSec;    // reported at completion
    private Instant createdAt;
}
