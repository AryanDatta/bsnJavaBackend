package com.bsn.backend.social.common;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Keyset-pagination cursor: base64url("epochMillis|id").
 * Stable under inserts; no OFFSET scans (ARCHITECTURE.md §3.3).
 */
public final class CursorUtil {

    private CursorUtil() {
    }

    public record Cursor(long millis, String id) {
    }

    public static String encode(long millis, String id) {
        String raw = millis + "|" + (id == null ? "" : id);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int i = raw.indexOf('|');
            return new Cursor(Long.parseLong(raw.substring(0, i)), raw.substring(i + 1));
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid cursor");
        }
    }

    public static int clampLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 20;
        }
        return Math.min(limit, 50);
    }
}
