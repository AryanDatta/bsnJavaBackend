package com.bsn.backend.social.service;

import com.bsn.backend.social.common.CursorUtil;
import com.bsn.backend.social.model.FeedEntry;
import com.bsn.backend.social.model.Follow;
import com.bsn.backend.social.model.Post;
import com.bsn.backend.social.model.UserInterestProfile;
import com.bsn.backend.social.repo.FeedEntryRepository;
import com.bsn.backend.social.repo.FollowRepository;
import com.bsn.backend.social.repo.PostRepository;
import com.bsn.backend.social.repo.UserInterestProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Home feed (§3). Hybrid fan-out:
 * - normal authors  → fan-out-on-write into feed_entries
 * - celebrity authors (≥ CELEB_THRESHOLD followers) → merged live at read time
 * Final ranking happens at read time with the viewer's interest profile:
 *   score = 0.30·affinity + 0.20·interestMatch + 0.20·velocityNorm + 0.20·freshness + 0.10·mehnatBoost
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedService {

    public static final long CELEB_THRESHOLD = 10_000;
    private static final int FANOUT_BATCH = 500;

    private final FeedEntryRepository feedEntries;
    private final FollowRepository follows;
    private final PostRepository posts;
    private final UserInterestProfileRepository interests;
    private final RecoService recoService;

    /* ── write path: fan-out on post going LIVE (§3.1) ─────── */

    @Async
    public void fanout(Post post) {
        try {
            long followerCount = follows.countByFolloweeId(post.getAuthorId());
            if (followerCount >= CELEB_THRESHOLD) {
                return; // celebs are merged at read time
            }
            double baseScore = mehnatBoost(post);
            int page = 0;
            List<Follow> batch;
            do {
                batch = follows.findByFolloweeId(post.getAuthorId(), PageRequest.of(page++, FANOUT_BATCH));
                List<FeedEntry> entries = new ArrayList<>(batch.size());
                for (Follow f : batch) {
                    entries.add(FeedEntry.builder()
                            .ownerId(f.getFollowerId())
                            .postId(post.getId())
                            .authorId(post.getAuthorId())
                            .baseScore(baseScore)
                            .reason(post.getSquadId() != null ? "SQUAD"
                                    : post.getChallengeId() != null ? "CHALLENGE" : "FOLLOWING")
                            .createdAt(post.getCreatedAt())
                            .build());
                }
                if (!entries.isEmpty()) {
                    try {
                        feedEntries.saveAll(entries);
                    } catch (Exception dup) {
                        // unique (ownerId, postId) — retries are safe
                    }
                }
            } while (batch.size() == FANOUT_BATCH);
        } catch (Exception e) {
            log.error("feed fanout failed for post {}", post.getId(), e);
        }
    }

    /* ── read path (§3.3) ──────────────────────────────────── */

    public Map<String, Object> homeFeed(String userId, String cursor, Integer limit) {
        int lim = CursorUtil.clampLimit(limit);
        CursorUtil.Cursor c = CursorUtil.decode(cursor);
        Instant before = c == null ? Instant.now().plusSeconds(60) : Instant.ofEpochMilli(c.millis());

        // candidates: 3x page from precomputed entries
        List<FeedEntry> entries = feedEntries.findByOwnerIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                userId, before, PageRequest.of(0, lim * 3));

        Map<String, FeedEntry> byPostId = new HashMap<>();
        entries.forEach(e -> byPostId.putIfAbsent(e.getPostId(), e));

        List<Post> candidates = new ArrayList<>(posts.findAllById(byPostId.keySet()).stream()
                .filter(p -> "LIVE".equals(p.getStatus())).toList());

        UserInterestProfile interest = interests.findById(userId).orElse(null);

        // rank
        List<Map<String, Object>> ranked = new ArrayList<>();
        for (Post p : candidates) {
            FeedEntry entry = byPostId.get(p.getId());
            double score = score(interest, p);
            Map<String, Object> item = new HashMap<>();
            item.put("post", p);
            item.put("reason", entry.getReason());
            item.put("score", score);
            item.put("entryCreatedAt", entry.getCreatedAt());
            ranked.add(item);
        }
        ranked.sort((a, b) -> Double.compare((double) b.get("score"), (double) a.get("score")));

        // author-diversity pass: max 2 consecutive per author (§3.2 penalties)
        ranked = diversityPass(ranked);

        // mixing policy (§4.4): inject reco every 5th slot → ~70/20/10
        List<Map<String, Object>> mixed = injectReco(userId, interest, ranked, lim, candidates);

        String nextCursor = "";
        if (!entries.isEmpty() && entries.size() >= lim) {
            Instant oldest = entries.get(entries.size() - 1).getCreatedAt();
            nextCursor = CursorUtil.encode(oldest.toEpochMilli(), "");
        }
        return Map.of("items", mixed, "nextCursor", nextCursor);
    }

    /* ── scoring (§3.2) ────────────────────────────────────── */

    public double score(UserInterestProfile viewer, Post post) {
        double affinity = 0.5, interestMatch = 0.0;
        if (viewer != null) {
            if (viewer.getCreators() != null) {
                affinity = viewer.getCreators().getOrDefault(post.getAuthorId(), 0.0);
            }
            interestMatch = recoService.interestMatch(viewer, post);
        }
        double velocityNorm = Math.min(1.0, post.getVelocity() / 100.0);
        double ageHours = Math.max(0, Duration.between(
                post.getCreatedAt() == null ? Instant.now() : post.getCreatedAt(), Instant.now()).toMinutes() / 60.0);
        double freshness = Math.exp(-ageHours / 24.0);

        return 0.30 * Math.min(1, affinity)
                + 0.20 * interestMatch
                + 0.20 * velocityNorm
                + 0.20 * freshness
                + 0.10 * mehnatBoost(post);
    }

    /** Verified effort outranks entertainment — that's the product (§3.2). */
    public double mehnatBoost(Post post) {
        double boost = 0;
        if (post.getVerification() != null && "VERIFIED".equals(post.getVerification().getStatus())) {
            boost += 0.5;
        }
        if (post.getChallengeId() != null) {
            boost += 0.2;
        }
        if (post.getSquadId() != null) {
            boost += 0.1;
        }
        return Math.min(1.0, boost);
    }

    /* ── helpers ───────────────────────────────────────────── */

    private List<Map<String, Object>> diversityPass(List<Map<String, Object>> ranked) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<Map<String, Object>> deferred = new ArrayList<>();
        String lastAuthor = null;
        int run = 0;
        for (Map<String, Object> item : ranked) {
            String author = ((Post) item.get("post")).getAuthorId();
            if (author.equals(lastAuthor) && run >= 2) {
                deferred.add(item);
                continue;
            }
            run = author.equals(lastAuthor) ? run + 1 : 1;
            lastAuthor = author;
            out.add(item);
        }
        out.addAll(deferred);
        return out;
    }

    private List<Map<String, Object>> injectReco(String userId, UserInterestProfile interest,
                                                 List<Map<String, Object>> ranked, int limit,
                                                 List<Post> alreadyIncluded) {
        Set<String> included = new HashSet<>();
        alreadyIncluded.forEach(p -> included.add(p.getId()));

        List<Post> recos = recoService.candidatesFor(userId, interest, included, Math.max(2, limit / 5) + 2);
        List<Map<String, Object>> out = new ArrayList<>();
        int recoIdx = 0, rankedIdx = 0;
        for (int slot = 0; out.size() < limit && (rankedIdx < ranked.size() || recoIdx < recos.size()); slot++) {
            boolean recoSlot = (slot + 1) % 5 == 0; // every 5th slot ≈ 20%
            if (recoSlot && recoIdx < recos.size()) {
                Post p = recos.get(recoIdx++);
                Map<String, Object> item = new HashMap<>();
                item.put("post", p);
                item.put("reason", "RECO");
                item.put("score", score(interest, p));
                out.add(item);
            } else if (rankedIdx < ranked.size()) {
                out.add(ranked.get(rankedIdx++));
            } else if (recoIdx < recos.size()) {
                Post p = recos.get(recoIdx++);
                Map<String, Object> item = new HashMap<>();
                item.put("post", p);
                item.put("reason", "RECO");
                item.put("score", score(interest, p));
                out.add(item);
            }
        }
        return out;
    }
}
