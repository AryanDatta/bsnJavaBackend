package com.bsn.backend.social.service;

import com.bsn.backend.exception.ResourceNotFoundException;
import com.bsn.backend.social.common.ConflictException;
import com.bsn.backend.social.model.Streak;
import com.bsn.backend.social.repo.StreakRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Streak engine (§5.2). Day-bucketing is timezone-correct — never UTC midnight (§8.2).
 * Heatmap: month -> bit string, one char per day ("every square = one verified video").
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreakService {

    private final StreakRepository streaks;
    private final NotificationService notifications;

    public Streak me(String userId) {
        return streaks.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("streak not found"));
    }

    /** Called by VerificationService on every verified video. */
    public void recordVerified(String userId, String localDate, String tz) {
        Streak s = streaks.findById(userId).orElseGet(() -> Streak.builder()
                .userId(userId).tz(tz).heatmap(new HashMap<>()).freezesUsed(new ArrayList<>()).build());

        if (localDate.equals(s.getLastVerifiedLocalDate())) {
            return; // already counted today
        }
        LocalDate today = LocalDate.parse(localDate);
        boolean consecutive = s.getLastVerifiedLocalDate() != null
                && LocalDate.parse(s.getLastVerifiedLocalDate()).plusDays(1).equals(today);

        s.setCurrent(consecutive ? s.getCurrent() + 1 : 1);
        s.setLongest(Math.max(s.getLongest(), s.getCurrent()));
        s.setLastVerifiedLocalDate(localDate);
        markHeatmap(s, today, '1');
        s.setGraceDeadlineAt(endOfDay(today.plusDays(1), s.getTz() == null ? tz : s.getTz()));
        streaks.save(s);
    }

    /** "2 streak freezes included" — freeze preserves the streak for today without a video. */
    public Streak useFreeze(String userId) {
        Streak s = me(userId);
        String today = LocalDate.now(zone(s.getTz())).toString();
        if (today.equals(s.getLastVerifiedLocalDate())) {
            throw new ConflictException("already covered today");
        }
        if (s.getFreezesAvailable() <= 0) {
            throw new ConflictException("no freezes available");
        }
        s.setFreezesAvailable(s.getFreezesAvailable() - 1);
        if (s.getFreezesUsed() == null) {
            s.setFreezesUsed(new ArrayList<>());
        }
        s.getFreezesUsed().add(Streak.FreezeUse.builder().date(today).source("MANUAL").build());
        s.setLastVerifiedLocalDate(today);
        markHeatmap(s, LocalDate.parse(today), '1');
        s.setGraceDeadlineAt(endOfDay(LocalDate.parse(today).plusDays(1), s.getTz()));
        return streaks.save(s);
    }

    public void addFreezes(String userId, int count) {
        streaks.findById(userId).ifPresent(s -> {
            s.setFreezesAvailable(s.getFreezesAvailable() + count);
            streaks.save(s);
        });
    }

    /** StreakTick job (§7.3): break streaks whose grace deadline passed without a video. */
    public int tick() {
        int broken = 0;
        for (Streak s : streaks.findByGraceDeadlineAtBefore(Instant.now())) {
            String today = LocalDate.now(zone(s.getTz())).toString();
            if (today.equals(s.getLastVerifiedLocalDate())) {
                continue;
            }
            int lost = s.getCurrent();
            s.setCurrent(0);
            s.setGraceDeadlineAt(null);
            markHeatmap(s, LocalDate.now(zone(s.getTz())).minusDays(1), '0');
            streaks.save(s);
            if (lost > 0) {
                notifications.push(s.getUserId(), "STREAK_RISK", null, "streak", s.getUserId(),
                        "Your " + lost + "-day streak ended. Record today to start again.");
                broken++;
            }
        }
        return broken;
    }

    /* ── helpers ──────────────────────────────────────────── */

    private void markHeatmap(Streak s, LocalDate day, char bit) {
        if (s.getHeatmap() == null) {
            s.setHeatmap(new HashMap<>());
        }
        String key = String.format("%d-%02d", day.getYear(), day.getMonthValue());
        String bits = s.getHeatmap().getOrDefault(key, "");
        StringBuilder sb = new StringBuilder(bits);
        while (sb.length() < day.getDayOfMonth()) {
            sb.append('0');
        }
        sb.setCharAt(day.getDayOfMonth() - 1, bit);
        s.getHeatmap().put(key, sb.toString());
    }

    static Instant endOfDay(LocalDate day, String tz) {
        return day.atTime(23, 59, 59).atZone(zone(tz)).toInstant();
    }

    public static ZoneId zone(String tz) {
        try {
            return ZoneId.of(tz == null ? AuthService.DEFAULT_TZ : tz);
        } catch (Exception e) {
            return ZoneId.of(AuthService.DEFAULT_TZ);
        }
    }
}
