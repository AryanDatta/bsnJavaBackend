package com.bsn.backend.social.service;

import com.bsn.backend.exception.ResourceNotFoundException;
import com.bsn.backend.social.model.MediaUpload;
import com.bsn.backend.social.model.SocialProfile;
import com.bsn.backend.social.model.Story;
import com.bsn.backend.social.model.StoryView;
import com.bsn.backend.social.repo.StoryRepository;
import com.bsn.backend.social.repo.StoryViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StoryService {

    private static final long TTL_SECONDS = 86400; // 24h

    private final StoryRepository stories;
    private final StoryViewRepository views;
    private final ProfileService profileService;
    private final MediaService mediaService;

    public Story create(String userId, String uploadId, String type, boolean verifiedClip) {
        SocialProfile author = profileService.byUserId(userId);
        MediaUpload upload = mediaService.consume(userId, uploadId);
        Instant now = Instant.now();
        return stories.save(Story.builder()
                .authorId(userId)
                .authorHandle(author.getHandle())
                .authorAvatarUrl(author.getAvatarUrl())
                .url("IMAGE".equals(type) ? mediaService.rawUrl(uploadId) : mediaService.hlsUrl(uploadId))
                .thumbUrl(mediaService.thumbUrl(uploadId))
                .durationSec(upload.getDurationSec() == null ? 0 : upload.getDurationSec())
                .type(type == null ? "VIDEO" : type)
                .verifiedClip(verifiedClip)
                .createdAt(now)
                .expiresAt(now.plusSeconds(TTL_SECONDS))
                .build());
    }

    /**
     * Stories tray (§3.4): unseen-first, then recency.
     * Squad-pending pinning is applied by SquadService on the client payload.
     */
    public List<Map<String, Object>> tray(String userId) {
        List<String> followees = profileService.followeeIds(userId);
        if (followees.isEmpty()) {
            return List.of();
        }
        List<Story> active = stories.findByAuthorIdInAndExpiresAtAfterOrderByCreatedAtDesc(followees, Instant.now());
        if (active.isEmpty()) {
            return List.of();
        }

        Set<String> seenStoryIds = new HashSet<>();
        for (StoryView v : views.findByViewerIdAndAuthorIdIn(userId, followees)) {
            seenStoryIds.add(v.getStoryId());
        }

        Map<String, List<Story>> byAuthor = new LinkedHashMap<>();
        active.forEach(s -> byAuthor.computeIfAbsent(s.getAuthorId(), k -> new ArrayList<>()).add(s));

        List<Map<String, Object>> tray = new ArrayList<>();
        for (Map.Entry<String, List<Story>> e : byAuthor.entrySet()) {
            List<Story> authorStories = e.getValue();
            boolean hasUnseen = authorStories.stream().anyMatch(s -> !seenStoryIds.contains(s.getId()));
            Map<String, Object> ring = new HashMap<>();
            ring.put("author", profileService.brief(e.getKey()));
            ring.put("hasUnseen", hasUnseen);
            ring.put("verifiedRing", authorStories.stream().anyMatch(Story::isVerifiedClip));
            ring.put("latestAt", authorStories.get(0).getCreatedAt());
            ring.put("storyCount", authorStories.size());
            tray.add(ring);
        }
        tray.sort(Comparator
                .comparing((Map<String, Object> m) -> (Boolean) m.get("hasUnseen"), Comparator.reverseOrder())
                .thenComparing(m -> (Instant) m.get("latestAt"), Comparator.reverseOrder()));
        return tray;
    }

    public List<Map<String, Object>> byAuthor(String viewerId, String authorId) {
        List<Story> list = stories.findByAuthorIdAndExpiresAtAfterOrderByCreatedAtAsc(authorId, Instant.now());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Story s : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("story", s);
            m.put("seen", views.existsByStoryIdAndViewerId(s.getId(), viewerId));
            out.add(m);
        }
        return out;
    }

    public void view(String viewerId, String storyId) {
        Story story = stories.findById(storyId)
                .orElseThrow(() -> new ResourceNotFoundException("story not found: " + storyId));
        try {
            views.save(StoryView.builder()
                    .storyId(storyId).viewerId(viewerId).authorId(story.getAuthorId())
                    .viewedAt(Instant.now()).build());
        } catch (DuplicateKeyException ignored) {
            // idempotent
        }
    }
}
