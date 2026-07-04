package com.bsn.backend.social.service;

import com.bsn.backend.exception.ResourceNotFoundException;
import com.bsn.backend.social.common.ConflictException;
import com.bsn.backend.social.common.ForbiddenException;
import com.bsn.backend.social.model.Comment;
import com.bsn.backend.social.model.EngagementEvent;
import com.bsn.backend.social.model.Like;
import com.bsn.backend.social.model.MediaUpload;
import com.bsn.backend.social.model.Post;
import com.bsn.backend.social.model.SocialProfile;
import com.bsn.backend.social.repo.ChallengeRepository;
import com.bsn.backend.social.repo.CommentRepository;
import com.bsn.backend.social.repo.EngagementEventRepository;
import com.bsn.backend.social.repo.FeedEntryRepository;
import com.bsn.backend.social.repo.LikeRepository;
import com.bsn.backend.social.repo.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository posts;
    private final CommentRepository comments;
    private final LikeRepository likes;
    private final EngagementEventRepository events;
    private final FeedEntryRepository feedEntries;
    private final ChallengeRepository challenges;
    private final ProfileService profileService;
    private final MediaService mediaService;
    private final FeedService feedService;
    private final RecoService recoService;
    private final NotificationService notifications;
    private final MongoTemplate mongo;

    /* ── create / read / delete ───────────────────────────── */

    public Post create(String userId, String type, String uploadId, String caption, List<String> tags,
                       String challengeId, String squadId, String visibility, boolean requestVerification) {
        SocialProfile author = profileService.byUserId(userId);

        List<String> normTags = new ArrayList<>();
        if (tags != null) {
            tags.stream().map(t -> t.toLowerCase().trim().replace("#", ""))
                    .filter(t -> !t.isBlank()).distinct().forEach(normTags::add);
        }
        if (challengeId != null) {
            challenges.findById(challengeId).ifPresentOrElse(
                    c -> normTags.add("challenge:" + c.getSlug()),
                    () -> {
                        throw new ResourceNotFoundException("challenge not found: " + challengeId);
                    });
        }

        Post.Media media = null;
        if (uploadId != null) {
            MediaUpload upload = mediaService.consume(userId, uploadId);
            media = Post.Media.builder()
                    .rawKey(uploadId)
                    .hlsUrl(mediaService.hlsUrl(uploadId))
                    .thumbUrl(mediaService.thumbUrl(uploadId))
                    .durationSec(upload.getDurationSec() == null ? 0 : upload.getDurationSec())
                    .build();
        } else if ("REEL".equals(type) || requestVerification) {
            throw new IllegalArgumentException("reels require an uploadId");
        }

        Post post = Post.builder()
                .authorId(userId)
                .authorHandle(author.getHandle())
                .authorAvatarUrl(author.getAvatarUrl())
                .authorCity(author.getCity())
                .type(type == null ? "REEL" : type)
                .caption(caption).tags(normTags)
                .media(media)
                .verification(requestVerification
                        ? Post.VerificationInfo.builder().status("PENDING").build()
                        : null)
                .challengeId(challengeId).squadId(squadId)
                .visibility(visibility == null ? "PUBLIC" : visibility)
                .counts(Post.Counts.builder().build())
                .velocity(0)
                // verifiable reels go LIVE when verification passes (§7.1)
                .status(requestVerification ? "PROCESSING" : "LIVE")
                .createdAt(Instant.now())
                .build();
        post = posts.save(post);

        if ("LIVE".equals(post.getStatus())) {
            onPostLive(post);
        }
        return post;
    }

    /** Counter bumps + fan-out, shared by casual posts and just-verified reels. */
    public void onPostLive(Post post) {
        profileService.incStat(post.getAuthorId(), "stats.posts", 1);
        if (post.getTags() != null) {
            post.getTags().forEach(this::bumpHashtag);
        }
        feedService.fanout(post);
    }

    public Post get(String postId) {
        return posts.findById(postId)
                .filter(p -> !"REMOVED".equals(p.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("post not found: " + postId));
    }

    public Map<String, Object> getWithViewerState(String postId, String viewerId) {
        Post post = get(postId);
        Map<String, Object> m = new HashMap<>();
        m.put("post", post);
        m.put("likedByMe", likes.existsBySubjectTypeAndSubjectIdAndUserId("POST", postId, viewerId));
        return m;
    }

    public void delete(String userId, String postId) {
        Post post = get(postId);
        if (!post.getAuthorId().equals(userId)) {
            throw new ForbiddenException("not your post");
        }
        post.setStatus("REMOVED");
        posts.save(post);
        profileService.incStat(userId, "stats.posts", -1);
        feedEntries.deleteByPostId(postId); // feed cleanup fan-out
    }

    public List<Post> byAuthor(String authorId, int page, int size) {
        return posts.findByAuthorIdAndStatusOrderByCreatedAtDesc(
                authorId, "LIVE", PageRequest.of(page, Math.min(size, 50)));
    }

    /* ── likes ────────────────────────────────────────────── */

    public long like(String userId, String postId) {
        Post post = get(postId);
        try {
            likes.save(Like.builder()
                    .subjectType("POST").subjectId(postId).userId(userId)
                    .createdAt(Instant.now()).build());
        } catch (DuplicateKeyException e) {
            throw new ConflictException("already liked");
        }
        incCount(postId, "counts.likes", 1);
        notifications.push(post.getAuthorId(), "LIKE", userId, "post", postId, "liked your post");
        recoService.applyEvent(userId, post, "LIKE", 0);
        return post.getCounts().getLikes() + 1;
    }

    public void unlike(String userId, String postId) {
        if (likes.existsBySubjectTypeAndSubjectIdAndUserId("POST", postId, userId)) {
            likes.deleteBySubjectTypeAndSubjectIdAndUserId("POST", postId, userId);
            incCount(postId, "counts.likes", -1);
        }
    }

    /* ── comments ─────────────────────────────────────────── */

    public Comment addComment(String userId, String postId, String parentId, String text) {
        if (text == null || text.isBlank() || text.length() > 2000) {
            throw new IllegalArgumentException("comment must be 1-2000 chars");
        }
        Post post = get(postId);
        SocialProfile author = profileService.byUserId(userId);
        Comment comment = comments.save(Comment.builder()
                .postId(postId).authorId(userId).authorHandle(author.getHandle())
                .parentId(parentId).text(text).likes(0).status("LIVE")
                .createdAt(Instant.now()).build());
        incCount(postId, "counts.comments", 1);
        notifications.push(post.getAuthorId(), "COMMENT", userId, "post", postId, "commented: " + trim(text));
        recoService.applyEvent(userId, post, "COMMENT", 0);
        return comment;
    }

    public List<Comment> listComments(String postId, int page, int size) {
        return comments.findByPostIdAndStatusOrderByCreatedAtDesc(
                postId, "LIVE", PageRequest.of(page, Math.min(size, 50)));
    }

    /* ── share ────────────────────────────────────────────── */

    public Map<String, Object> share(String userId, String postId) {
        Post post = get(postId);
        incCount(postId, "counts.shares", 1);
        recoService.applyEvent(userId, post, "SHARE", 0);
        return Map.of("shareUrl", "https://mehnat.app/p/" + postId);
    }

    /* ── engagement events (batched, fire-and-forget §6.4) ── */

    public int ingestEvents(String userId, List<Map<String, Object>> batch) {
        if (batch == null || batch.isEmpty() || batch.size() > 100) {
            throw new IllegalArgumentException("events batch must be 1-100 items");
        }
        int accepted = 0;
        for (Map<String, Object> e : batch) {
            String postId = (String) e.get("postId");
            String type = (String) e.get("type");
            if (postId == null || type == null) {
                continue;
            }
            Post post = posts.findById(postId).orElse(null);
            if (post == null) {
                continue;
            }
            long dwellMs = e.get("dwellMs") instanceof Number n ? n.longValue() : 0;
            events.save(EngagementEvent.builder()
                    .userId(userId).postId(postId).authorId(post.getAuthorId())
                    .type(type).dwellMs(dwellMs).tags(post.getTags())
                    .source((String) e.getOrDefault("source", "FEED"))
                    .createdAt(Instant.now()).build());
            if ("VIEW".equals(type) || "COMPLETE_VIEW".equals(type)) {
                incCount(postId, "counts.views", 1);
            }
            recoService.applyEvent(userId, post, type, dwellMs);
            accepted++;
        }
        return accepted;
    }

    /* ── helpers ──────────────────────────────────────────── */

    public void incCount(String postId, String path, long delta) {
        mongo.updateFirst(new Query(Criteria.where("_id").is(postId)),
                new Update().inc(path, delta), Post.class);
    }

    private void bumpHashtag(String tag) {
        mongo.upsert(new Query(Criteria.where("_id").is(tag)),
                new Update().inc("postCount", 1).inc("last24hCount", 1).set("updatedAt", Instant.now()),
                com.bsn.backend.social.model.HashtagStat.class);
    }

    private String trim(String text) {
        return text.length() > 60 ? text.substring(0, 60) + "…" : text;
    }
}
