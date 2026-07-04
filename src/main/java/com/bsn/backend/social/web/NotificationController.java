package com.bsn.backend.social.web;

import com.bsn.backend.social.common.SecurityUtil;
import com.bsn.backend.social.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "mehnat · notifications")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "notification feed with unread count")
    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String cursor,
                                    @RequestParam(required = false) Integer limit) {
        return notificationService.list(SecurityUtil.currentUserId(), cursor, limit);
    }

    @Operation(summary = "mark all read")
    @PostMapping("/read")
    public Map<String, String> markRead() {
        notificationService.markAllRead(SecurityUtil.currentUserId());
        return Map.of("message", "all read");
    }
}
