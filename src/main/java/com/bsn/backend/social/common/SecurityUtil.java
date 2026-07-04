package com.bsn.backend.social.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtil {

    private SecurityUtil() {
    }

    /** Returns the authenticated userId set by JwtAuthFilter, or throws. */
    public static String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || "anonymousUser".equals(auth.getPrincipal())) {
            throw new UnauthorizedException("authentication required");
        }
        return (String) auth.getPrincipal();
    }
}
