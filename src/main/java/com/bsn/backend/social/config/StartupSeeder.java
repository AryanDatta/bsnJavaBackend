package com.bsn.backend.social.config;

import com.bsn.backend.social.model.Challenge;
import com.bsn.backend.social.model.Season;
import com.bsn.backend.social.model.StoreItem;
import com.bsn.backend.social.repo.ChallengeRepository;
import com.bsn.backend.social.repo.SeasonRepository;
import com.bsn.backend.social.repo.StoreItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Ensures critical indexes (§2.2) and seeds the active season, a demo challenge
 * and store shelves on first boot. Index creation is explicit because the app
 * uses a custom MongoTemplate (annotation auto-indexing doesn't apply).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupSeeder implements ApplicationRunner {

    private final SeasonRepository seasons;
    private final ChallengeRepository challenges;
    private final StoreItemRepository storeItems;
    private final MongoTemplate mongo;

    @Override
    public void run(ApplicationArguments args) {
        ensureIndexes();
        seedSeason();
        seedChallenge();
        seedStore();
    }

    private void ensureIndexes() {
        try {
            // uniqueness that the domain logic depends on
            unique("follows", "followerId", "followeeId");
            unique("likes", "subjectType", "subjectId", "userId");
            unique("feed_entries", "ownerId", "postId");
            unique("wallet_ledger", "idempotencyKey");
            unique("challenge_participants", "challengeId", "userId");
            unique("season_ranks", "userId", "seasonId");
            unique("squad_daily_status", "squadId", "localDate");
            unique("story_views", "storyId", "viewerId");
            unique("refresh_tokens", "tokenHash");
            unique("user_profiles", "handle");
            unique("verifications", "postId");
            // hot query paths
            index("posts", Sort.Direction.DESC, "authorId", "createdAt");
            index("posts", Sort.Direction.DESC, "challengeId", "createdAt");
            index("posts", Sort.Direction.DESC, "tags", "createdAt");
            index("posts", Sort.Direction.DESC, "velocity", "createdAt");
            index("feed_entries", Sort.Direction.DESC, "ownerId", "createdAt");
            index("engagement_events", Sort.Direction.DESC, "userId", "createdAt");
            index("wallet_ledger", Sort.Direction.DESC, "userId", "createdAt");
            index("notifications", Sort.Direction.DESC, "userId", "createdAt");
            index("season_ranks", Sort.Direction.DESC, "seasonId", "seasonPts");
            index("comments", Sort.Direction.DESC, "postId", "createdAt");
            log.info("mehnat indexes ensured");
        } catch (Exception e) {
            log.warn("index creation skipped: {}", e.getMessage());
        }
    }

    private void unique(String collection, String... fields) {
        Index idx = new Index();
        for (String f : fields) {
            idx = idx.on(f, Sort.Direction.ASC);
        }
        mongo.indexOps(collection).createIndex(idx.unique().sparse());
    }

    private void index(String collection, Sort.Direction dir, String... fields) {
        Index idx = new Index();
        for (String f : fields) {
            idx = idx.on(f, dir);
        }
        mongo.indexOps(collection).createIndex(idx);
    }

    private void seedSeason() {
        if (seasons.findByActiveTrue().isPresent()) {
            return;
        }
        seasons.save(Season.builder()
                .id("S1")
                .startAt(Instant.now())
                .endAt(Instant.now().plus(90, ChronoUnit.DAYS))
                .active(true)
                .build());
        log.info("seeded season S1");
    }

    private void seedChallenge() {
        if (challenges.findBySlug("fitzone-30").isPresent()) {
            return;
        }
        // payout: 7*5 + 13*15 + 10*44 = 670 daily + finish bonus → "77% in the final stretch"
        challenges.save(Challenge.builder()
                .slug("fitzone-30").sponsor("FitZone")
                .title("30-day gym challenge")
                .description("Show up. Record. Every day. That's the whole deal.")
                .durationDays(30)
                .startAt(Instant.now())
                .endAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .maxPoints(1000)
                .payoutCurve(List.of(
                        Challenge.CurveSegment.builder().fromDay(1).toDay(7).ptsPerDay(5).build(),
                        Challenge.CurveSegment.builder().fromDay(8).toDay(20).ptsPerDay(15).build(),
                        Challenge.CurveSegment.builder().fromDay(21).toDay(30).ptsPerDay(44).build()))
                .rules(Challenge.Rules.builder()
                        .videosPerDay(1).freezesIncluded(2).quitPenaltyPts(50).build())
                .friendBonus(Challenge.FriendBonus.builder().threshold(3).bonusPts(100).build())
                .stats(Challenge.Stats.builder().build())
                .status("ACTIVE")
                .build());
        log.info("seeded challenge fitzone-30");
    }

    private void seedStore() {
        if (storeItems.count() > 0) {
            return;
        }
        storeItems.saveAll(List.of(
                StoreItem.builder().aisle("REWARD").name("Protein tub voucher")
                        .sub("FitZone partner stores").pricePts(1200).tag("PARTNER")
                        .kycRequired(true).status("LIVE").build(),
                StoreItem.builder().aisle("REWARD").name("Gym day pass")
                        .sub("any partner gym").pricePts(500).tag("PARTNER")
                        .kycRequired(true).status("LIVE").build(),
                StoreItem.builder().aisle("COSMETIC").name("Ember profile ring")
                        .sub("animated flex-card ring").pricePts(300).tag("COSMETIC")
                        .kycRequired(false).status("LIVE").build(),
                StoreItem.builder().aisle("COSMETIC").name("Immortal flame theme")
                        .sub("profile + squad theme").pricePts(800).tag("IMMORTAL ONLY")
                        .minTier("IMMORTAL").kycRequired(false).status("LIVE").build()));
        log.info("seeded store items");
    }
}
