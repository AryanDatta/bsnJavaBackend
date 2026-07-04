package com.bsn.backend.social.web;

import com.bsn.backend.social.common.SecurityUtil;
import com.bsn.backend.social.model.Story;
import com.bsn.backend.social.service.StoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "mehnat · stories", description = "24h ephemeral content, unseen-first tray (§6.4, §3.4)")
@RestController
@RequestMapping("/api/v1/stories")
@RequiredArgsConstructor
public class StoryController {

    private final StoryService storyService;

    public record CreateStoryRequest(String uploadId, String type, Boolean verifiedClip) {
    }

    @Operation(summary = "post a story (24h TTL)")
    @PostMapping
    public ResponseEntity<Story> create(@RequestBody CreateStoryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(storyService.create(
                SecurityUtil.currentUserId(), req.uploadId(), req.type(),
                Boolean.TRUE.equals(req.verifiedClip())));
    }

    @Operation(summary = "stories tray — unseen-first rings (§3.4)")
    @GetMapping("/tray")
    public List<Map<String, Object>> tray() {
        return storyService.tray(SecurityUtil.currentUserId());
    }

    @Operation(summary = "an author's active stories, oldest first")
    @GetMapping("/{authorId}")
    public List<Map<String, Object>> byAuthor(@PathVariable String authorId) {
        return storyService.byAuthor(SecurityUtil.currentUserId(), authorId);
    }

    @Operation(summary = "mark story viewed")
    @PostMapping("/{id}/view")
    public Map<String, String> view(@PathVariable String id) {
        storyService.view(SecurityUtil.currentUserId(), id);
        return Map.of("message", "viewed");
    }
}
