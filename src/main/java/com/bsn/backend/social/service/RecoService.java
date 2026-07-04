package com.bsn.backend.social.service;

import com.bsn.backend.social.model.HashtagStat;
import com.bsn.backend.social.model.Post;
import com.bsn.backend.social.model.SocialProfile;
import com.bsn.backend.social.model.UserInterestProfile;
import com.bsn.backend.social.repo.FollowRepository;
import com.bsn.backend.social.repo.HashtagStatRepository;
import com.bsn.backend.social.repo.SocialProfileRepository;
import com.bsn.backend.social.repo.UserInterestProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DB-only recommendation engine (§4): candidate generation → scoring → mixing.
 * The interest profile is the "learning loop without ML" (§4.3) — swap the scorer
 * for an ML ranker in Phase 3 behind this same interface.
 */
@Service
@RequiredArgsConstructor
public class RecoService {

    private static final Map<String, Double> EVENT_WEIGHTS = Map.of(
            "VIEW", 0.5, "COMPLETE_VIEW", 2.0, "LIKE", 3.0, "COMMENT", 5.0,
            "SHARE", 7.0, "JOIN_CLICK", 4.0, "SKIP", -2.0, "REPORT", -5.0);

    private final UserInterestProfileRepository interests;
    private final SocialProfileRepository profiles;
    private final FollowRepository follows;
    private final HashtagStatRepository hashtags;
    private final MongoTemplate mongo;

    /* ── interest profile maintenance (§4.3, incremental) ──── */

    public void applyEvent(String userId, Post post, String type, long dwellMs) {
        double weight = EVENT_WEIGHTS.getOrDefault(type, 0.0);
        if (dwellMs > 15000) {
            weight += 0.5; // long dwell is a signal too
        }
        if (weight == 0.0) {
            return;
        }
        UserInterestProfile profile = interests.findById(userId).orElseGet(() ->
                UserInterestProfile.builder()
                        .userId(userId).tags(new HashMap<>()).creators(new HashMap<>())
                        .lastRecomputedAt(Instant.now()).build());
        if (profile.getTags() == null) {
            profile.setTags(new HashMap<>());
        }
        if (profile.getCreators() == null) {
            profile.setCreators(new HashMap<>());
        }
        if (post.getTags() != null) {
            for (String tag : post.getTags()) {
                profile.getTags().merge(tag, weight / 10.0, Double::sum);
            }
        }
        profile.getCreators().merge(post.getAuthorId(), weight / 10.0, Double::sum);
        clamp(profile.getTags());
        clamp(profile.getCreators());
        interests.save(profile);
    }

    /** Cosine-ish match: mean viewer affinity over the post's tags, clamped to 0..1. */
    public double interestMatch(UserInterestProfile viewer, Post post) {
        if (viewer == null || viewer.getTags() == null || post.getTags() == null || post.getTags().isEmpty()) {
            return 0.0;
        }
        double sum = 0;
        for (String tag : post.getTags()) {
            sum += viewer.getTags().getOrDefault(tag, 0.0);
        }
        return Math.max(0.0, Math.min(1.0, sum / post.getTags().size()));
    }

    /* ── candidate generation (§4.2) ───────────────────────── */

    /** Budgeted candidate pull for feed injection and explore. */
    public List<Post> candidatesFor(String userId, UserInterestProfile interest, Set<String> excludePostIds, int n) {
        Set<String> exclude = excludePostIds == null ? new HashSet<>() : new HashSet<>(excludePostIds);
        Set<String> followedAuthors = new HashSet<>(follows.findByFollowerId(userId).stream()
                .map(f -> f.getFolloweeId()).toList());
        followedAuthors.add(userId); // never recommend own posts

        Instant window = Instant.now().minus(Duration.ofHours(48));
        Map<String, Post> pool = new LinkedHashMap<>();

        // 1. trending: verified, high velocity, 48h
        trendingQuery(window, 30).forEach(p -> pool.putIfAbsent(p.getId(), p));

        // 2. same-city verified reels
        String city = profiles.findById(userId).map(SocialProfile::getCity).orElse(null);
        if (city != null) {
            cityQuery(city, window, 30).forEach(p -> pool.putIfAbsent(p.getId(), p));
        }

        // 3. tag neighbors: viewer's top-5 interest tags
        if (interest != null && interest.getTags() != null && !interest.getTags().isEmpty()) {
            List<String> topTags = interest.getTags().entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(5).map(Map.Entry::getKey).toList();
            tagQuery(topTags, window, 30).forEach(p -> pool.putIfAbsent(p.getId(), p));
        }

        return pool.values().stream()
                .filter(p -> !exclude.contains(p.getId()))
                .filter(p -> !followedAuthors.contains(p.getAuthorId()))
                .sorted(Comparator.comparingDouble((Post p) -> exploreScore(interest, p)).reversed())
                .limit(n)
                .toList();
    }

    /* ── explore tab: 100% reco/trending (§4.4) ────────────── */

    public Map<String, Object> explore(String userId, String tag, String city, Integer limit) {
        int lim = limit == null ? 30 : Math.min(limit, 50);
        UserInterestProfile interest = interests.findById(userId).orElse(null);
        List<Post> items;
        Instant window = Instant.now().minus(Duration.ofHours(72));
        if (tag != null && !tag.isBlank()) {
            items = tagQuery(List.of(tag.toLowerCase()), window, lim);
        } else if (city != null && !city.isBlank()) {
            items = cityQuery(city, window, lim);
        } else {
            items = candidatesFor(userId, interest, Set.of(), lim);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Post p : items) {
            Map<String, Object> m = new HashMap<>();
            m.put("post", p);
            m.put("reason", tag != null ? "TAG" : city != null ? "CITY" : "RECO");
            out.add(m);
        }
        return Map.of("items", out);
    }

    /* ── search (Phase 1: prefix/regex — ES in Phase 2) ────── */

    public Map<String, Object> search(String q) {
        String query = q == null ? "" : q.trim().toLowerCase().replace("#", "");
        if (query.length() < 2) {
            throw new IllegalArgumentException("query must be at least 2 chars");
        }
        List<Map<String, Object>> users = profiles
                .findByHandleStartingWithIgnoreCase(query, PageRequest.of(0, 10))
                .stream().map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("userId", p.getUserId());
                    m.put("handle", p.getHandle());
                    m.put("displayName", p.getDisplayName());
                    m.put("avatarUrl", p.getAvatarUrl());
                    m.put("verifiedHuman", p.isVerifiedHuman());
                    return (Map<String, Object>) m;
                }).toList();

        Query tagQ = new Query(Criteria.where("_id").regex("^" + java.util.regex.Pattern.quote(query)))
                .with(Sort.by(Sort.Direction.DESC, "trendScore")).limit(10);
        List<HashtagStat> tags = mongo.find(tagQ, HashtagStat.class);

        return Map.of("users", users, "tags", tags);
    }

    public List<HashtagStat> trendingTags() {
        return hashtags.findTop20ByOrderByTrendScoreDesc();
    }

    /* ── nightly decay (§4.3) — invoked by ScheduledJobs ───── */

    public void decayInterestProfiles() {
        for (UserInterestProfile p : interests.findAll()) {
            boolean dirty = false;
            if (p.getTags() != null) {
                dirty |= decayMap(p.getTags());
            }
            if (p.getCreators() != null) {
                dirty |= decayMap(p.getCreators());
            }
            if (dirty) {
                p.setLastRecomputedAt(Instant.now());
                interests.save(p);
            }
        }
    }

    /* ── internals ─────────────────────────────────────────── */

    double exploreScore(UserInterestProfile interest, Post p) {
        double velocityNorm = Math.min(1.0, p.getVelocity() / 100.0);
        double ageHours = Math.max(0, Duration.between(
                p.getCreatedAt() == null ? Instant.now() : p.getCreatedAt(), Instant.now()).toMinutes() / 60.0);
        double freshness = Math.exp(-ageHours / 24.0);
        double verifiedBoost = p.getVerification() != null
                && "VERIFIED".equals(p.getVerification().getStatus()) ? 0.5 : 0.0;
        return 0.3 * interestMatch(interest, p) + 0.3 * velocityNorm + 0.2 * freshness + 0.2 * verifiedBoost;
    }

    private List<Post> trendingQuery(Instant window, int n) {
        Query q = new Query(Criteria.where("status").is("LIVE")
                .and("createdAt").gte(window)
                .and("verification.status").is("VERIFIED"))
                .with(Sort.by(Sort.Direction.DESC, "velocity")).limit(n);
        return mongo.find(q, Post.class);
    }

    private List<Post> cityQuery(String city, Instant window, int n) {
        Query q = new Query(Criteria.where("status").is("LIVE")
                .and("createdAt").gte(window)
                .and("authorCity").is(city))
                .with(Sort.by(Sort.Direction.DESC, "velocity")).limit(n);
        return mongo.find(q, Post.class);
    }

    private List<Post> tagQuery(List<String> tags, Instant window, int n) {
        Query q = new Query(Criteria.where("status").is("LIVE")
                .and("createdAt").gte(window)
                .and("tags").in(tags))
                .with(Sort.by(Sort.Direction.DESC, "velocity")).limit(n);
        return mongo.find(q, Post.class);
    }

    private void clamp(Map<String, Double> map) {
        map.replaceAll((k, v) -> Math.max(-1.0, Math.min(1.0, v)));
        map.values().removeIf(v -> Math.abs(v) < 0.01);
    }

    private boolean decayMap(Map<String, Double> map) {
        if (map.isEmpty()) {
            return false;
        }
        map.replaceAll((k, v) -> v * 0.95);
        map.values().removeIf(v -> Math.abs(v) < 0.01);
        return true;
    }
}
