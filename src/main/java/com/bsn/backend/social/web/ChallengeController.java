package com.bsn.backend.social.web;

import com.bsn.backend.social.common.SecurityUtil;
import com.bsn.backend.social.model.Challenge;
import com.bsn.backend.social.model.ChallengeParticipant;
import com.bsn.backend.social.model.Post;
import com.bsn.backend.social.service.ChallengeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "mehnat · challenges", description = "sponsored challenges, back-loaded payouts (§6.8)")
@RestController
@CrossOrigin
@RequestMapping("/api/v1/challenges")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeService challengeService;

    @Operation(summary = "discover challenges (payout curve included)")
    @GetMapping
    public List<Challenge> list(@RequestParam(required = false) String status,
                                @RequestParam(required = false) String city,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        return challengeService.list(status, city, page, size);
    }

    @Operation(summary = "challenge detail — joined, finish rate, curve, friend bonus")
    @GetMapping("/{slug}")
    public Challenge get(@PathVariable String slug) {
        return challengeService.bySlug(slug);
    }

    @Operation(summary = "join — grants freezes, checks friend bonus")
    @PostMapping("/{slug}/join")
    public ResponseEntity<ChallengeParticipant> join(@PathVariable String slug) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(challengeService.join(SecurityUtil.currentUserId(), slug));
    }

    @Operation(summary = "quit — penalty applies, unmatured points forfeited")
    @PostMapping("/{slug}/quit")
    public Map<String, Object> quit(@PathVariable String slug) {
        return challengeService.quit(SecurityUtil.currentUserId(), slug);
    }

    @Operation(summary = "my progress — day, verified days, freezes left")
    @GetMapping("/{slug}/me")
    public Map<String, Object> me(@PathVariable String slug) {
        return challengeService.myProgress(SecurityUtil.currentUserId(), slug);
    }

    @Operation(summary = "challenge leaderboard")
    @GetMapping("/{slug}/leaderboard")
    public List<Map<String, Object>> leaderboard(@PathVariable String slug,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return challengeService.leaderboard(slug, page, size);
    }

    @Operation(summary = "day-scrubber feed of verified posts (D1 → D30)")
    @GetMapping("/{slug}/feed")
    public List<Post> feed(@PathVariable String slug,
                           @RequestParam(required = false) Integer day,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size) {
        return challengeService.feed(slug, day, page, size);
    }
}
