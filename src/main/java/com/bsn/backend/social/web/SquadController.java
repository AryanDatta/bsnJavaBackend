package com.bsn.backend.social.web;

import com.bsn.backend.social.common.SecurityUtil;
import com.bsn.backend.social.model.Squad;
import com.bsn.backend.social.model.SquadDailyStatus;
import com.bsn.backend.social.service.SquadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "mehnat · squads", description = "accountability groups — streak survives only if everyone records (§6.7)")
@RestController
@CrossOrigin
@RequestMapping("/api/v1/squads")
@RequiredArgsConstructor
public class SquadController {

    private final SquadService squadService;

    public record CreateSquadRequest(String name) {
    }

    public record JoinRequest(String inviteCode) {
    }

    public record ThemeRequest(String theme) {
    }

    @Operation(summary = "create a squad (max 8 members)")
    @PostMapping
    public ResponseEntity<Squad> create(@RequestBody CreateSquadRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(squadService.create(SecurityUtil.currentUserId(), req.name()));
    }

    @Operation(summary = "my squads")
    @GetMapping("/mine")
    public List<Squad> mine() {
        return squadService.mine(SecurityUtil.currentUserId());
    }

    @Operation(summary = "squad detail — members with today's done/pending")
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return squadService.get(id, SecurityUtil.currentUserId());
    }

    @Operation(summary = "join via invite code")
    @PostMapping("/join")
    public Squad join(@RequestBody JoinRequest req) {
        return squadService.join(SecurityUtil.currentUserId(), req.inviteCode());
    }

    @Operation(summary = "leave squad")
    @DeleteMapping("/{id}/members/me")
    public Map<String, String> leave(@PathVariable String id) {
        squadService.leave(SecurityUtil.currentUserId(), id);
        return Map.of("message", "left squad");
    }

    @Operation(summary = "today's squad status — 'Arjun pending · 6h left'")
    @GetMapping("/{id}/today")
    public SquadDailyStatus today(@PathVariable String id) {
        return squadService.today(id);
    }

    @Operation(summary = "nudge a pending member (rate-limited 3/day)")
    @PostMapping("/{id}/nudge/{userId}")
    public Map<String, Object> nudge(@PathVariable String id, @PathVariable String userId) {
        return squadService.nudge(SecurityUtil.currentUserId(), id, userId);
    }

    @Operation(summary = "theme gallery — gold+ themes locked below GOLD")
    @GetMapping("/{id}/theme")
    public Map<String, Object> themes(@PathVariable String id) {
        return squadService.themes(id);
    }

    @Operation(summary = "set squad theme")
    @PutMapping("/{id}/theme")
    public Squad setTheme(@PathVariable String id, @RequestBody ThemeRequest req) {
        return squadService.setTheme(SecurityUtil.currentUserId(), id, req.theme());
    }
}
