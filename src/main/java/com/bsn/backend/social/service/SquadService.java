package com.bsn.backend.social.service;

import com.bsn.backend.exception.ResourceNotFoundException;
import com.bsn.backend.social.common.ConflictException;
import com.bsn.backend.social.common.ForbiddenException;
import com.bsn.backend.social.model.SocialProfile;
import com.bsn.backend.social.model.Squad;
import com.bsn.backend.social.model.SquadDailyStatus;
import com.bsn.backend.social.model.Tier;
import com.bsn.backend.social.repo.SquadDailyStatusRepository;
import com.bsn.backend.social.repo.SquadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Squads (§2.2): "If Arjun misses, the squad streak resets to 0 for everyone. That's the point."
 */
@Service
@RequiredArgsConstructor
public class SquadService {

    private static final List<String> GOLD_PLUS_THEMES = List.of("gold_ember", "onyx", "immortal_flame");

    private final SquadRepository squads;
    private final SquadDailyStatusRepository dailyStatuses;
    private final ProfileService profileService;
    private final NotificationService notifications;

    public Squad create(String userId, String name) {
        if (name == null || name.isBlank() || name.length() > 40) {
            throw new IllegalArgumentException("squad name must be 1-40 chars");
        }
        List<String> members = new ArrayList<>();
        members.add(userId);
        return squads.save(Squad.builder()
                .name(name.trim()).ownerId(userId).memberIds(members)
                .inviteCode(UUID.randomUUID().toString().substring(0, 8))
                .streakCurrent(0).rule("ALL_MUST_RECORD")
                .theme("default").themeUnlocks(new ArrayList<>(List.of("default")))
                .createdAt(Instant.now())
                .build());
    }

    public Map<String, Object> get(String squadId, String viewerId) {
        Squad squad = bySquadId(squadId);
        Map<String, Object> out = new HashMap<>();
        out.put("squad", squad);
        List<Map<String, Object>> members = new ArrayList<>();
        SquadDailyStatus today = todayStatus(squad);
        for (String memberId : squad.getMemberIds()) {
            Map<String, Object> m = profileService.brief(memberId);
            m.put("doneToday", today.getDone() != null && today.getDone().contains(memberId));
            members.add(m);
        }
        out.put("members", members);
        out.put("today", today);
        out.put("isMember", squad.getMemberIds().contains(viewerId));
        return out;
    }

    public List<Squad> mine(String userId) {
        return squads.findByMemberIdsContaining(userId);
    }

    public Squad join(String userId, String inviteCode) {
        Squad squad = squads.findByInviteCode(inviteCode)
                .orElseThrow(() -> new ResourceNotFoundException("invalid invite code"));
        if (squad.getMemberIds().contains(userId)) {
            throw new ConflictException("already a member");
        }
        if (squad.getMemberIds().size() >= Squad.MAX_MEMBERS) {
            throw new ConflictException("squad is full (max " + Squad.MAX_MEMBERS + ")");
        }
        squad.getMemberIds().add(userId);
        // joining resets the collective streak clock fairness-wise: new member starts pending today
        squads.save(squad);
        SocialProfile joiner = profileService.byUserId(userId);
        for (String memberId : squad.getMemberIds()) {
            notifications.push(memberId, "SQUAD", userId, "squad", squad.getId(),
                    "@" + joiner.getHandle() + " joined " + squad.getName());
        }
        return squad;
    }

    public void leave(String userId, String squadId) {
        Squad squad = bySquadId(squadId);
        if (!squad.getMemberIds().remove(userId)) {
            throw new ConflictException("not a member");
        }
        if (squad.getMemberIds().isEmpty()) {
            squads.delete(squad);
            return;
        }
        if (userId.equals(squad.getOwnerId())) {
            squad.setOwnerId(squad.getMemberIds().get(0));
        }
        squads.save(squad);
    }

    public SquadDailyStatus today(String squadId) {
        return todayStatus(bySquadId(squadId));
    }

    /** "Arjun pending · 6h left. Nudge him." — rate-limited 3/day per target. */
    public Map<String, Object> nudge(String actorId, String squadId, String targetId) {
        Squad squad = bySquadId(squadId);
        if (!squad.getMemberIds().contains(actorId) || !squad.getMemberIds().contains(targetId)) {
            throw new ForbiddenException("both users must be squad members");
        }
        SocialProfile actor = profileService.byUserId(actorId);
        boolean sent = notifications.pushLimited(targetId, "NUDGE", actorId, "squad", squadId,
                "@" + actor.getHandle() + " nudged you — squad streak is on the line. Record now.", 3);
        return Map.of("sent", sent, "note", sent ? "nudge delivered" : "nudge limit reached for today");
    }

    /* ── themes ("Locked below Gold") ─────────────────────── */

    public Map<String, Object> themes(String squadId) {
        Squad squad = bySquadId(squadId);
        return Map.of("active", squad.getTheme(), "unlocked", squad.getThemeUnlocks(),
                "goldPlus", GOLD_PLUS_THEMES);
    }

    public Squad setTheme(String userId, String squadId, String theme) {
        Squad squad = bySquadId(squadId);
        if (!squad.getMemberIds().contains(userId)) {
            throw new ForbiddenException("not a member");
        }
        if (GOLD_PLUS_THEMES.contains(theme)) {
            SocialProfile p = profileService.byUserId(userId);
            Tier tier = Tier.valueOf(p.getRank() == null ? "IRON" : p.getRank().getTier());
            if (tier.minRr() < Tier.GOLD.minRr()) {
                throw new ForbiddenException("theme locked below GOLD — rank opens the shelf");
            }
        }
        squad.setTheme(theme);
        if (!squad.getThemeUnlocks().contains(theme)) {
            squad.getThemeUnlocks().add(theme);
        }
        return squads.save(squad);
    }

    /* ── verification hook + nightly resolve ──────────────── */

    /** Called when a member's video is verified: pending → done; all done → squad streak +1. */
    public void onVerified(String userId, String localDate) {
        for (Squad squad : squads.findByMemberIdsContaining(userId)) {
            SquadDailyStatus status = statusFor(squad, localDate);
            if (status.getDone() == null) {
                status.setDone(new ArrayList<>());
            }
            if (status.getPending() == null) {
                status.setPending(new ArrayList<>());
            }
            if (!status.getDone().contains(userId)) {
                status.getDone().add(userId);
                status.getPending().remove(userId);
            }
            if (status.getPending().isEmpty() && status.getResolvedAt() == null) {
                status.setResolvedAt(Instant.now());
                squad.setStreakCurrent(squad.getStreakCurrent() + 1);
                squad.setLastCompleteLocalDate(localDate);
                squads.save(squad);
                for (String memberId : squad.getMemberIds()) {
                    notifications.push(memberId, "SQUAD", null, "squad", squad.getId(),
                            squad.getName() + " streak → " + squad.getStreakCurrent() + ". Everyone recorded.");
                }
            }
            dailyStatuses.save(status);
        }
    }

    /** Nightly: any squad with unresolved pending members yesterday → streak resets to 0. */
    public int resolveDay(String localDate) {
        int resets = 0;
        for (Squad squad : squads.findAll()) {
            SquadDailyStatus status = dailyStatuses.findBySquadIdAndLocalDate(squad.getId(), localDate)
                    .orElse(null);
            boolean incomplete = status == null
                    || (status.getPending() != null && !status.getPending().isEmpty());
            if (incomplete && squad.getStreakCurrent() > 0) {
                squad.setStreakCurrent(0);
                squads.save(squad);
                resets++;
                for (String memberId : squad.getMemberIds()) {
                    notifications.push(memberId, "SQUAD", null, "squad", squad.getId(),
                            squad.getName() + " streak reset to 0 — someone missed. That's the point.");
                }
            }
        }
        return resets;
    }

    /* ── helpers ──────────────────────────────────────────── */

    private Squad bySquadId(String squadId) {
        return squads.findById(squadId)
                .orElseThrow(() -> new ResourceNotFoundException("squad not found: " + squadId));
    }

    private SquadDailyStatus todayStatus(Squad squad) {
        String today = LocalDate.now(StreakService.zone(AuthService.DEFAULT_TZ)).toString();
        return statusFor(squad, today);
    }

    private SquadDailyStatus statusFor(Squad squad, String localDate) {
        return dailyStatuses.findBySquadIdAndLocalDate(squad.getId(), localDate)
                .orElseGet(() -> dailyStatuses.save(SquadDailyStatus.builder()
                        .squadId(squad.getId()).localDate(localDate)
                        .done(new ArrayList<>())
                        .pending(new ArrayList<>(squad.getMemberIds()))
                        .build()));
    }
}
