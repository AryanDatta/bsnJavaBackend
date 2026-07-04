package com.bsn.backend.social.web;

import com.bsn.backend.social.common.SecurityUtil;
import com.bsn.backend.social.model.Comment;
import com.bsn.backend.social.model.Post;
import com.bsn.backend.social.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "mehnat · posts & engagement", description = "reels, likes, comments, shares, events (§6.4)")
@RestController
@CrossOrigin
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    public record CreatePostRequest(String type, String uploadId, String caption, List<String> tags,
                                    String challengeId, String squadId, String visibility,
                                    Boolean requestVerification) {
    }

    public record CommentRequest(String parentId, String text) {
    }

    /* ── posts ────────────────────────────────────────────── */

    @Operation(summary = "create post/reel — requestVerification=true keeps it PROCESSING until verified (§7.1)")
    @PostMapping("/posts")
    public ResponseEntity<Post> create(@RequestBody CreatePostRequest req) {
        Post post = postService.create(SecurityUtil.currentUserId(),
                req.type(), req.uploadId(), req.caption(), req.tags(),
                req.challengeId(), req.squadId(), req.visibility(),
                Boolean.TRUE.equals(req.requestVerification()));
        return ResponseEntity.status(HttpStatus.CREATED).body(post);
    }

    @Operation(summary = "get post with viewer state")
    @GetMapping("/posts/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return postService.getWithViewerState(id, SecurityUtil.currentUserId());
    }

    @Operation(summary = "delete my post — triggers feed-entry cleanup fan-out")
    @DeleteMapping("/posts/{id}")
    public Map<String, String> delete(@PathVariable String id) {
        postService.delete(SecurityUtil.currentUserId(), id);
        return Map.of("message", "deleted");
    }

    /* ── likes ────────────────────────────────────────────── */

    @Operation(summary = "like a post")
    @PostMapping("/posts/{id}/like")
    public Map<String, Object> like(@PathVariable String id) {
        return Map.of("likes", postService.like(SecurityUtil.currentUserId(), id));
    }

    @Operation(summary = "unlike a post")
    @DeleteMapping("/posts/{id}/like")
    public Map<String, String> unlike(@PathVariable String id) {
        postService.unlike(SecurityUtil.currentUserId(), id);
        return Map.of("message", "unliked");
    }

    /* ── comments ─────────────────────────────────────────── */

    @Operation(summary = "comment (parentId for replies)")
    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<Comment> comment(@PathVariable String id, @RequestBody CommentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(postService.addComment(SecurityUtil.currentUserId(), id, req.parentId(), req.text()));
    }

    @Operation(summary = "list comments")
    @GetMapping("/posts/{id}/comments")
    public List<Comment> comments(@PathVariable String id,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return postService.listComments(id, page, size);
    }

    /* ── share ────────────────────────────────────────────── */

    @Operation(summary = "share — returns share link, counts the event")
    @PostMapping("/posts/{id}/share")
    public Map<String, Object> share(@PathVariable String id) {
        return postService.share(SecurityUtil.currentUserId(), id);
    }

    /* ── engagement events (fire-and-forget batch §6.4) ───── */

    @Operation(summary = "batched engagement events [{postId,type,dwellMs,source}] — feeds the reco engine")
    @PostMapping("/events")
    public Map<String, Object> events(@RequestBody List<Map<String, Object>> batch) {
        int accepted = postService.ingestEvents(SecurityUtil.currentUserId(), batch);
        return Map.of("accepted", accepted);
    }
}
