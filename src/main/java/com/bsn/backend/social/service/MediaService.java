package com.bsn.backend.social.service;

import com.bsn.backend.exception.ResourceNotFoundException;
import com.bsn.backend.social.common.ForbiddenException;
import com.bsn.backend.social.config.JwtService;
import com.bsn.backend.social.model.MediaUpload;
import com.bsn.backend.social.repo.MediaUploadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Direct-to-storage upload sessions (§6.3). Phase 1 stubs the presigned URL —
 * swap `uploadBaseUrl` for real S3 presigning without touching callers.
 */
@Service
@RequiredArgsConstructor
public class MediaService {

    private final MediaUploadRepository uploads;
    private final JwtService jwt; // reused for capture-token HMAC

    @Value("${mehnat.media.upload-base-url:https://storage.mehnat.app/upload}")
    private String uploadBaseUrl;

    @Value("${mehnat.media.cdn-base-url:https://cdn.mehnat.app}")
    private String cdnBaseUrl;

    public Map<String, Object> createUploadSession(String userId) {
        String uploadId = UUID.randomUUID().toString();
        String captureToken = jwt.hmac("capture:" + uploadId + ":" + userId);
        uploads.save(MediaUpload.builder()
                .id(uploadId).userId(userId).captureToken(captureToken)
                .status("PENDING").createdAt(Instant.now())
                .build());
        return Map.of(
                "uploadId", uploadId,
                "uploadUrl", uploadBaseUrl + "/" + uploadId,
                "captureToken", captureToken,
                "expiresInSeconds", 3600
        );
    }

    public Map<String, Object> complete(String userId, String uploadId, Integer durationSec) {
        MediaUpload upload = owned(userId, uploadId);
        upload.setStatus("UPLOADED");
        upload.setDurationSec(durationSec == null ? 0 : durationSec);
        uploads.save(upload);
        return Map.of("uploadId", uploadId, "status", "UPLOADED");
    }

    /** Marks the upload consumed by a post and returns it. */
    public MediaUpload consume(String userId, String uploadId) {
        MediaUpload upload = owned(userId, uploadId);
        if (!"UPLOADED".equals(upload.getStatus())) {
            throw new IllegalArgumentException("upload not completed yet");
        }
        upload.setStatus("CONSUMED");
        return uploads.save(upload);
    }

    public boolean captureTokenValid(String userId, String uploadId, String captureToken) {
        return captureToken != null
                && captureToken.equals(jwt.hmac("capture:" + uploadId + ":" + userId));
    }

    public String hlsUrl(String uploadId) {
        return cdnBaseUrl + "/hls/" + uploadId + "/master.m3u8";
    }

    public String thumbUrl(String uploadId) {
        return cdnBaseUrl + "/thumb/" + uploadId + ".jpg";
    }

    public String rawUrl(String uploadId) {
        return cdnBaseUrl + "/raw/" + uploadId;
    }

    private MediaUpload owned(String userId, String uploadId) {
        MediaUpload upload = uploads.findById(uploadId)
                .orElseThrow(() -> new ResourceNotFoundException("upload not found: " + uploadId));
        if (!upload.getUserId().equals(userId)) {
            throw new ForbiddenException("not your upload");
        }
        return upload;
    }
}
