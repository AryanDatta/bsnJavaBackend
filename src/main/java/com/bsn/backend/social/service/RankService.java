package com.bsn.backend.social.service;

import com.bsn.backend.exception.ResourceNotFoundException;
import com.bsn.backend.social.model.SeasonRank;
import com.bsn.backend.social.model.SocialProfile;
import com.bsn.backend.social.model.Tier;
import com.bsn.backend.social.repo.SeasonRankRepository;
import com.bsn.backend.social.repo.SeasonRepository;
import com.bsn.backend.social.repo.SocialProfileRepository;
import lombok.RequiredArgsConstructor;
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

/**
 * The ladder (§5.3). RR rises on verified effort only; multiplier is a pure
 * function of tier; decay after 3 idle days. Rank snapshot mirrored to the profile.
 */
@Service
@RequiredArgsConstructor
public class RankService {

    private static final int DECAY_RR_PER_DAY = 5;
    private static final long DECAY_IDLE_SECONDS = 3 * 86400;

    private final SeasonRepository seasons;
    private final SeasonRankRepository ranks;
    private final SocialProfileRepository profiles;
    private final NotificationService notifications;
    private final MongoTemplate mongo;

    public String currentSeasonId() {
        return seasons.findByActiveTrue().map(s -> s.getId()).orElse("S1");
    }

    public double multiplierOf(String userId) {
        return profiles.findById(userId)
                .map(p -> p.getRank() == null ? Tier.IRON.multiplier() : p.getRank().getMultiplier())
                .orElse(Tier.IRON.multiplier());
    }

    /** Award RR + season points for a verified action; keeps tier/multiplier in sync. */
    public void award(String userId, int deltaRr, long pts, String reason) {
        String seasonId = currentSeasonId();
        SeasonRank rank = ranks.findByUserIdAndSeasonId(userId, seasonId).orElseGet(() -> {
            String city = profiles.findById(userId).map(SocialProfile::getCity).orElse(null);
            return SeasonRank.builder()
                    .userId(userId).seasonId(seasonId).city(city)
                    .tier(Tier.IRON.name()).rr(0).seasonPts(0)
                    .peakTier(Tier.IRON.name()).heldSince(Instant.now())
                    .history(new ArrayList<>()).build();
        });

        Tier before = Tier.valueOf(rank.getTier());
        rank.setRr(Math.max(0, rank.getRr() + deltaRr));
        rank.setSeasonPts(rank.getSeasonPts() + Math.max(0, pts));
        rank.setLastEarnAt(Instant.now());
        rank.setDecayActive(false);

        Tier after = Tier.forRr(rank.getRr());
        rank.setTier(after.name());
        if (after.minRr() > Tier.valueOf(rank.getPeakTier() == null ? "IRON" : rank.getPeakTier()).minRr()) {
            rank.setPeakTier(after.name());
        }
        if (after != before) {
            rank.setHeldSince(Instant.now());
            notifications.push(userId, "RANK_DECAY", null, "rank", rank.getId(),
                    after.minRr() > before.minRr()
                            ? "Promoted to " + after.name() + " · " + after.multiplier() + "x multiplier"
                            : "Demoted to " + after.name());
        }
        appendHistory(rank, deltaRr, reason);
        ranks.save(rank);
        mirrorToProfile(userId, rank, after);
    }

    public void creditChallengeFinish(String userId) {
        String seasonId = currentSeasonId();
        ranks.findByUserIdAndSeasonId(userId, seasonId).ifPresent(r -> {
            r.setChallengesFinished(r.getChallengesFinished() + 1);
            ranks.save(r);
        });
        award(userId, 25, 0, "CHALLENGE_FINISH");
    }

    public Map<String, Object> me(String userId) {
        String seasonId = currentSeasonId();
        SeasonRank rank = ranks.findByUserIdAndSeasonId(userId, seasonId)
                .orElseThrow(() -> new ResourceNotFoundException("no rank this season"));
        Tier tier = Tier.valueOf(rank.getTier());
        Map<String, Object> out = new HashMap<>();
        out.put("season", seasonId);
        out.put("tier", tier.name());
        out.put("rr", rank.getRr());
        out.put("displayRr", Tier.displayRr(rank.getRr()));
        out.put("multiplier", tier.multiplier());
        out.put("heldSince", rank.getHeldSince());
        out.put("decayActive", rank.isDecayActive());
        out.put("holdRequirements", Map.of(
                "rrAbove100", Tier.displayRr(rank.getRr()) >= 100,
                "challengesFinished", rank.getChallengesFinished(),
                "challengesRequired", 2
        ));
        out.put("history", rank.getHistory() == null ? List.of() : rank.getHistory());
        return out;
    }

    public List<Map<String, Object>> tiers() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Tier t : Tier.values()) {
            out.add(Map.of("name", t.name(), "minRr", t.minRr(), "multiplier", t.multiplier()));
        }
        return out;
    }

    /** "Seasons are a collection, not a reset" — the season shelf. */
    public List<SeasonRank> shelf(String userId) {
        return ranks.findByUserIdOrderBySeasonIdDesc(userId);
    }

    /** City leaderboard — "Chamba · you #14" (§6.9). */
    public Map<String, Object> cityLeaderboard(String viewerId, String city, int page, int size) {
        String seasonId = currentSeasonId();
        List<SeasonRank> rows = ranks.findBySeasonIdAndCityOrderBySeasonPtsDesc(
                seasonId, city, PageRequest.of(page, Math.min(size, 50)));
        List<Map<String, Object>> items = new ArrayList<>();
        int base = page * Math.min(size, 50);
        for (int i = 0; i < rows.size(); i++) {
            SeasonRank r = rows.get(i);
            Map<String, Object> m = new HashMap<>();
            m.put("rank", base + i + 1);
            m.put("userId", r.getUserId());
            m.put("handle", profiles.findById(r.getUserId()).map(SocialProfile::getHandle).orElse("?"));
            m.put("tier", r.getTier());
            m.put("pts", r.getSeasonPts());
            items.add(m);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("city", city);
        out.put("season", seasonId);
        out.put("items", items);
        ranks.findByUserIdAndSeasonId(viewerId, seasonId).ifPresent(mine ->
                out.put("myRank", ranks.countBySeasonIdAndCityAndSeasonPtsGreaterThan(
                        seasonId, city, mine.getSeasonPts()) + 1));
        return out;
    }

    /** Nightly RankDecay job (§7.3): −5 RR/day after 3 idle days. */
    public int decayScan() {
        String seasonId = currentSeasonId();
        Instant cutoff = Instant.now().minusSeconds(DECAY_IDLE_SECONDS);
        int decayed = 0;
        // start decay on newly idle users
        for (SeasonRank r : ranks.findBySeasonIdAndDecayActiveFalseAndLastEarnAtBefore(seasonId, cutoff)) {
            r.setDecayActive(true);
            ranks.save(r);
            notifications.push(r.getUserId(), "RANK_DECAY", null, "rank", r.getId(),
                    "No verified video in 3 days — RR decay has started. Record to stop it.");
        }
        // apply daily decay to all decay-active rows
        Query q = new Query(Criteria.where("seasonId").is(seasonId).and("decayActive").is(true).and("rr").gt(0));
        for (SeasonRank r : mongo.find(q, SeasonRank.class)) {
            r.setRr(Math.max(0, r.getRr() - DECAY_RR_PER_DAY));
            Tier t = Tier.forRr(r.getRr());
            r.setTier(t.name());
            appendHistory(r, -DECAY_RR_PER_DAY, "DECAY");
            ranks.save(r);
            mirrorToProfile(r.getUserId(), r, t);
            decayed++;
        }
        return decayed;
    }

    /* ── helpers ──────────────────────────────────────────── */

    private void appendHistory(SeasonRank rank, int delta, String reason) {
        if (rank.getHistory() == null) {
            rank.setHistory(new ArrayList<>());
        }
        rank.getHistory().add(SeasonRank.RrEvent.builder()
                .at(Instant.now()).delta(delta).reason(reason).build());
        if (rank.getHistory().size() > 100) {
            rank.setHistory(new ArrayList<>(rank.getHistory().subList(rank.getHistory().size() - 100,
                    rank.getHistory().size())));
        }
    }

    private void mirrorToProfile(String userId, SeasonRank rank, Tier tier) {
        mongo.updateFirst(new Query(Criteria.where("_id").is(userId)),
                new Update()
                        .set("rank.tier", tier.name())
                        .set("rank.rr", rank.getRr())
                        .set("rank.multiplier", tier.multiplier())
                        .set("rank.season", rank.getSeasonId())
                        .set("rank.heldSince", rank.getHeldSince()),
                SocialProfile.class);
    }
}
