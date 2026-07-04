package com.bsn.backend.social.web;

import com.bsn.backend.social.common.SecurityUtil;
import com.bsn.backend.social.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "mehnat · media", description = "direct-to-storage upload sessions (§6.3)")
@RestController
@CrossOrigin
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    public record CompleteRequest(Integer durationSec) {
    }

    @Operation(summary = "create upload session — returns presigned URL + captureToken")
    @PostMapping("/upload-session")
    public Map<String, Object> uploadSession() {
        return mediaService.createUploadSession(SecurityUtil.currentUserId());
    }

    @Operation(summary = "mark upload complete → triggers transcode (stubbed in Phase 1)")
    @PostMapping("/{uploadId}/complete")
    public Map<String, Object> complete(@PathVariable String uploadId, @RequestBody(required = false) CompleteRequest req) {
        return mediaService.complete(SecurityUtil.currentUserId(), uploadId,
                req == null ? null : req.durationSec());
    }
}
