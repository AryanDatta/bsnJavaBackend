package com.bsn.backend.social.config;

import com.bsn.backend.social.model.HashtagStat;
import com.bsn.backend.social.model.Post;
import com.bsn.backend.social.service.RankService;
import com.bsn.backend.social.service.RecoService;
import com.bsn.backend.social.service.SquadService;
import com.bsn.backend.social.service.StreakService;
import com.bsn.backend.social.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Phase 1 job runner (§7.3). Runs in-process via @Scheduled;
 * extract to a worker deployment when load demands it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledJobs {

    private final StreakService streakService;
    private final SquadService squadService;
    private final RankService rankService;
    private final RecoService recoService;
    private final WalletService walletService;
    private final MongoTemplate mongo;

    /** StreakTick — every 5 min: break streaks whose grace deadline passed. */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void streakTick() {
        int broken = streakService.tick();
        if (broken > 0) {
            log.info("streakTick: {} streaks broken", broken);
        }
    }

    /** VelocityRefresh — every 5 min: engagement velocity for ranking + trending (§3.2). */
    @Scheduled(fixedDelay = 300_000, initialDelay = 90_000)
    public void velocityRefresh() {
        Instant window = Instant.now().minus(Duration.ofHours(48));
        Query q = new Query(Criteria.where("status").is("LIVE").and("createdAt").gte(window));
        for (Post p : mongo.find(q, Post.class)) {
            double hours = Math.max(0.25,
                    Duration.between(p.getCreatedAt(), Instant.now()).toMinutes() / 60.0);
            long likes = p.getCounts() == null ? 0 : p.getCounts().getLikes();
            long comments = p.getCounts() == null ? 0 : p.getCounts().getComments();
            long shares = p.getCounts() == null ? 0 : p.getCounts().getShares();
            double velocity = (likes + 2.0 * comments + 3.0 * shares) / Math.pow(hours, 1.5);
            mongo.updateFirst(new Query(Criteria.where("_id").is(p.getId())),
                    new Update().set("velocity", velocity).set("velocityUpdatedAt", Instant.now()),
                    Post.class);
        }
    }

    /** PointsMaturity — hourly: vest matured challenge payouts. */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 120_000)
    public void pointsMaturity() {
        int matured = walletService.matureDuePoints();
        if (matured > 0) {
            log.info("pointsMaturity: {} entries vested", matured);
        }
    }

    /** Nightly 00:30 UTC: interest decay, rank decay, squad day resolve, hashtag trend refresh. */
    @Scheduled(cron = "0 30 0 * * *")
    public void nightly() {
        recoService.decayInterestProfiles();
        int decayed = rankService.decayScan();
        String yesterday = LocalDate.now(StreakService.zone(null)).minusDays(1).toString();
        int squadResets = squadService.resolveDay(yesterday);
        refreshHashtagTrends();
        log.info("nightly: rankDecayed={} squadResets={}", decayed, squadResets);
    }

    private void refreshHashtagTrends() {
        Instant dayAgo = Instant.now().minus(Duration.ofHours(24));
        for (HashtagStat stat : mongo.findAll(HashtagStat.class)) {
            long recent = mongo.count(new Query(Criteria.where("status").is("LIVE")
                    .and("tags").is(stat.getTag())
                    .and("createdAt").gte(dayAgo)), Post.class);
            mongo.updateFirst(new Query(Criteria.where("_id").is(stat.getTag())),
                    new Update().set("last24hCount", recent)
                            .set("trendScore", recent + 0.1 * stat.getPostCount())
                            .set("updatedAt", Instant.now()),
                    HashtagStat.class);
        }
    }
}
