package com.bsn.backend.social.web;

import com.bsn.backend.social.common.SecurityUtil;
import com.bsn.backend.social.model.Season;
import com.bsn.backend.social.model.Streak;
import com.bsn.backend.social.repo.SeasonRepository;
import com.bsn.backend.social.service.RankService;
import com.bsn.backend.social.service.StreakService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "mehnat · progression", description = "streaks, ladder, seasons, leaderboards (§6.7, §6.9)")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProgressionController {

    private final StreakService streakService;
    private final RankService rankService;
    private final SeasonRepository seasons;

    /* ── streaks ──────────────────────────────────────────── */

    @Operation(summary = "my streak — current, longest, heatmap, freezes, deadline")
    @GetMapping("/streaks/me")
    public Streak myStreak() {
        return streakService.me(SecurityUtil.currentUserId());
    }

    @Operation(summary = "consume a streak freeze for today")
    @PostMapping("/streaks/me/freeze")
    public Streak useFreeze() {
        return streakService.useFreeze(SecurityUtil.currentUserId());
    }

    /* ── ladder ───────────────────────────────────────────── */

    @Operation(summary = "my rank — tier, RR, multiplier, hold requirements, decay state")
    @GetMapping("/ranks/me")
    public Map<String, Object> myRank() {
        return rankService.me(SecurityUtil.currentUserId());
    }

    @Operation(summary = "ladder definition — tiers and multipliers")
    @GetMapping("/ranks/tiers")
    public List<Map<String, Object>> tiers() {
        return rankService.tiers();
    }

    /* ── seasons ──────────────────────────────────────────── */

    @Operation(summary = "current season")
    @GetMapping("/seasons/current")
    public Season currentSeason() {
        return seasons.findByActiveTrue().orElse(null);
    }

    /* ── leaderboards ("Chamba · you #14") ────────────────── */

    @Operation(summary = "city leaderboard with my rank (§6.9)")
    @GetMapping("/leaderboards/city/{city}")
    public Map<String, Object> cityLeaderboard(@PathVariable String city,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return rankService.cityLeaderboard(SecurityUtil.currentUserId(), city, page, size);
    }
}
