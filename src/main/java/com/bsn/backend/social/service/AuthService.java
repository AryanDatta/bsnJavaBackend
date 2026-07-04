package com.bsn.backend.social.service;

import com.bsn.backend.model.User;
import com.bsn.backend.repository.UserRepository;
import com.bsn.backend.social.common.ConflictException;
import com.bsn.backend.social.common.UnauthorizedException;
import com.bsn.backend.social.config.JwtService;
import com.bsn.backend.social.model.RefreshToken;
import com.bsn.backend.social.model.SeasonRank;
import com.bsn.backend.social.model.SocialProfile;
import com.bsn.backend.social.model.Streak;
import com.bsn.backend.social.model.Tier;
import com.bsn.backend.social.model.UserInterestProfile;
import com.bsn.backend.social.repo.RefreshTokenRepository;
import com.bsn.backend.social.repo.SeasonRankRepository;
import com.bsn.backend.social.repo.SeasonRepository;
import com.bsn.backend.social.repo.SocialProfileRepository;
import com.bsn.backend.social.repo.StreakRepository;
import com.bsn.backend.social.repo.UserInterestProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String DEFAULT_TZ = "Asia/Kolkata";
    private static final Pattern HANDLE = Pattern.compile("^[a-z0-9._]{3,20}$");
    private static final long REFRESH_TTL_DAYS = 30;

    private final UserRepository users;
    private final SocialProfileRepository profiles;
    private final StreakRepository streaks;
    private final UserInterestProfileRepository interests;
    private final SeasonRepository seasons;
    private final SeasonRankRepository seasonRanks;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;

    public record TokenPair(String accessToken, String refreshToken, String userId, String handle) {
    }

    public TokenPair register(String handle, String email, String password, String fullName,
                              String city, String tz, List<String> interestTags) {
        String h = handle == null ? "" : handle.trim().toLowerCase();
        if (!HANDLE.matcher(h).matches()) {
            throw new IllegalArgumentException("handle must match ^[a-z0-9._]{3,20}$");
        }
        if (users.existsByHandle(h) || profiles.findByHandle(h).isPresent()) {
            throw new ConflictException("handle already taken");
        }
        if (email == null || users.existsByEmail(email)) {
            throw new ConflictException("email already registered");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("password must be at least 8 characters");
        }

        User user = User.builder()
                .fullName(fullName).handle(h).email(email)
                .password(passwordEncoder.encode(password))
                .role("USER")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        user = users.save(user);

        String season = seasons.findByActiveTrue().map(s -> s.getId()).orElse("S1");
        String zone = tz == null || tz.isBlank() ? DEFAULT_TZ : tz;

        profiles.save(SocialProfile.builder()
                .userId(user.getId()).handle(h).displayName(fullName == null ? h : fullName)
                .city(city).tz(zone).memberSince(season)
                .verifiedHuman(false).privateAccount(false).kycStatus("NONE")
                .shareSlug(h)
                .stats(SocialProfile.Stats.builder().build())
                .rank(SocialProfile.RankInfo.builder()
                        .tier(Tier.IRON.name()).rr(0).multiplier(Tier.IRON.multiplier())
                        .season(season).heldSince(Instant.now()).build())
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build());

        streaks.save(Streak.builder()
                .userId(user.getId()).current(0).longest(0).tz(zone)
                .freezesAvailable(0).heatmap(new HashMap<>())
                .build());

        Map<String, Double> tagWeights = new HashMap<>();
        if (interestTags != null) {
            interestTags.forEach(t -> tagWeights.put(t.toLowerCase(), 0.5));
        }
        interests.save(UserInterestProfile.builder()
                .userId(user.getId()).tags(tagWeights).creators(new HashMap<>())
                .city(city).lastRecomputedAt(Instant.now())
                .build());

        seasonRanks.save(SeasonRank.builder()
                .userId(user.getId()).seasonId(season).city(city)
                .tier(Tier.IRON.name()).rr(0).seasonPts(0)
                .peakTier(Tier.IRON.name()).heldSince(Instant.now()).lastEarnAt(Instant.now())
                .history(new java.util.ArrayList<>())
                .build());

        return issuePair(user.getId(), h);
    }

    public TokenPair login(String emailOrHandle, String password) {
        String key = emailOrHandle == null ? "" : emailOrHandle.trim();
        User user = users.findByEmail(key)
                .or(() -> users.findByHandle(key.toLowerCase()))
                .orElseThrow(() -> new UnauthorizedException("invalid credentials"));
        if (user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new UnauthorizedException("invalid credentials");
        }
        return issuePair(user.getId(), user.getHandle());
    }

    public TokenPair refresh(String refreshToken) {
        RefreshToken stored = refreshTokens.findByTokenHash(sha256(refreshToken))
                .orElseThrow(() -> new UnauthorizedException("invalid refresh token"));
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            refreshTokens.deleteByTokenHash(stored.getTokenHash());
            throw new UnauthorizedException("refresh token expired");
        }
        refreshTokens.deleteByTokenHash(stored.getTokenHash()); // rotation
        String handle = users.findById(stored.getUserId()).map(User::getHandle).orElse(null);
        return issuePair(stored.getUserId(), handle);
    }

    public void logout(String refreshToken) {
        refreshTokens.deleteByTokenHash(sha256(refreshToken));
    }

    private TokenPair issuePair(String userId, String handle) {
        String refresh = UUID.randomUUID() + "." + UUID.randomUUID();
        refreshTokens.save(RefreshToken.builder()
                .userId(userId).tokenHash(sha256(refresh))
                .expiresAt(Instant.now().plusSeconds(REFRESH_TTL_DAYS * 86400))
                .createdAt(Instant.now())
                .build());
        return new TokenPair(jwt.issueAccessToken(userId, handle), refresh, userId, handle);
    }

    static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("sha256 unavailable", e);
        }
    }
}
