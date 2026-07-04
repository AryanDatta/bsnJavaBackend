package com.bsn.backend.social.web;

import com.bsn.backend.social.common.SecurityUtil;
import com.bsn.backend.social.model.Verification;
import com.bsn.backend.social.service.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "mehnat · verification", description = "the golden path: record → verify → points (§6.6, §7.1)")
@RestController
@CrossOrigin
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationService verificationService;

    public record SubmitRequest(String postId, String activityLabel, Integer effortSeconds,
                                String captureToken) {
    }

    public record DecideRequest(Boolean approve, String reason) {
    }

    @Operation(summary = "submit a video for verification — auto-verifies when checks pass, else manual queue")
    @PostMapping("/verifications")
    public ResponseEntity<Verification> submit(@RequestBody SubmitRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(verificationService.submit(
                SecurityUtil.currentUserId(), req.postId(), req.activityLabel(),
                req.effortSeconds() == null ? 0 : req.effortSeconds(), req.captureToken()));
    }

    @Operation(summary = "poll verification status")
    @GetMapping("/verifications/{id}")
    public Verification get(@PathVariable String id) {
        return verificationService.get(SecurityUtil.currentUserId(), id);
    }

    /* ── manual review (Phase 1 admin — add role gate before launch) ── */

    @Operation(summary = "review queue (admin)")
    @GetMapping("/admin/verifications")
    public List<Verification> queue(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return verificationService.reviewQueue(page, size);
    }

    @Operation(summary = "approve/reject a queued verification (admin)")
    @PostMapping("/admin/verifications/{id}/decide")
    public Verification decide(@PathVariable String id, @RequestBody DecideRequest req) {
        return verificationService.decide(SecurityUtil.currentUserId(), id,
                Boolean.TRUE.equals(req.approve()), req.reason());
    }
}
