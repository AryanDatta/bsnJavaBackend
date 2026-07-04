package com.bsn.backend.social.web;

import com.bsn.backend.social.common.SecurityUtil;
import com.bsn.backend.social.model.HashtagStat;
import com.bsn.backend.social.service.FeedService;
import com.bsn.backend.social.service.RecoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "mehnat · feed & discovery", description = "ranked home feed, explore, search, trending (§6.5)")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;
    private final RecoService recoService;

    @Operation(summary = "home feed — 70/20/10 following/reco/trending mix, items carry `reason` (§3.3, §4.4)")
    @GetMapping("/feed")
    public Map<String, Object> feed(@RequestParam(required = false) String cursor,
                                    @RequestParam(required = false) Integer limit) {
        return feedService.homeFeed(SecurityUtil.currentUserId(), cursor, limit);
    }

    @Operation(summary = "explore — 100% reco/trending, filterable by tag or city (§4.4)")
    @GetMapping("/explore")
    public Map<String, Object> explore(@RequestParam(required = false) String tag,
                                       @RequestParam(required = false) String city,
                                       @RequestParam(required = false) Integer limit) {
        return recoService.explore(SecurityUtil.currentUserId(), tag, city, limit);
    }

    @Operation(summary = "search users + tags (Phase 1: prefix match; ES in Phase 2)")
    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam String q) {
        return recoService.search(q);
    }

    @Operation(summary = "trending tags")
    @GetMapping("/tags/trending")
    public List<HashtagStat> trendingTags() {
        return recoService.trendingTags();
    }
}
