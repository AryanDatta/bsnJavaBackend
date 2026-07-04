package com.bsn.backend.social.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Dependency-free HS256 JWT implementation (no jjwt needed).
 * Access token: sub=userId, hnd=handle, exp, jti.
 */
@Service
public class JwtService {

    private static final Base64.Encoder B64E = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${mehnat.jwt.secret:dev-secret-change-me-in-prod-0123456789abcdef}")
    private String secret;

    @Value("${mehnat.jwt.access-ttl-minutes:1440}")
    private long accessTtlMinutes;

    public String issueAccessToken(String userId, String handle) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", userId);
        claims.put("hnd", handle);
        claims.put("iat", Instant.now().getEpochSecond());
        claims.put("exp", Instant.now().plusSeconds(accessTtlMinutes * 60).getEpochSecond());
        claims.put("jti", UUID.randomUUID().toString());
        try {
            String head = B64E.encodeToString(HEADER.getBytes(StandardCharsets.UTF_8));
            String body = B64E.encodeToString(mapper.writeValueAsBytes(claims));
            return head + "." + body + "." + sign(head + "." + body);
        } catch (Exception e) {
            throw new IllegalStateException("failed to issue token", e);
        }
    }

    /** Returns claims if the token is well-formed, correctly signed and unexpired. */
    public Optional<Map<String, Object>> verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }
            byte[] expected = signRaw(parts[0] + "." + parts[1]);
            byte[] actual = B64D.decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) {
                return Optional.empty();
            }
            Map<String, Object> claims = mapper.readValue(B64D.decode(parts[1]),
                    new TypeReference<Map<String, Object>>() {
                    });
            long exp = ((Number) claims.getOrDefault("exp", 0L)).longValue();
            if (Instant.now().getEpochSecond() >= exp) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** HMAC used for media capture-tokens too (proves in-app camera, ARCHITECTURE.md §6.3). */
    public String hmac(String payload) {
        return B64E.encodeToString(signRaw(payload));
    }

    private String sign(String payload) {
        return B64E.encodeToString(signRaw(payload));
    }

    private byte[] signRaw(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("hmac failure", e);
        }
    }
}
