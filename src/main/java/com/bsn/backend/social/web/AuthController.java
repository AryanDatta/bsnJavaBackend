package com.bsn.backend.social.web;

import com.bsn.backend.social.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "mehnat · auth", description = "register / login / refresh / logout (§6.1)")
@RestController
@CrossOrigin
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    public record RegisterRequest(String handle, String email, String password, String fullName,
                                  String city, String tz, List<String> interests) {
    }

    public record LoginRequest(String emailOrHandle, String password) {
    }

    public record TokenRequest(String refreshToken) {
    }

    @Operation(summary = "register — creates user + profile + streak + interest profile + season rank")
    @PostMapping("/register")
    public ResponseEntity<AuthService.TokenPair> register(@RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(
                req.handle(), req.email(), req.password(), req.fullName(),
                req.city(), req.tz(), req.interests()));
    }

    @Operation(summary = "login with email or handle")
    @PostMapping("/login")
    public AuthService.TokenPair login(@RequestBody LoginRequest req) {
        return authService.login(req.emailOrHandle(), req.password());
    }

    @Operation(summary = "rotate refresh token")
    @PostMapping("/refresh")
    public AuthService.TokenPair refresh(@RequestBody TokenRequest req) {
        return authService.refresh(req.refreshToken());
    }

    @Operation(summary = "revoke refresh token")
    @PostMapping("/logout")
    public Map<String, String> logout(@RequestBody TokenRequest req) {
        authService.logout(req.refreshToken());
        return Map.of("message", "logged out");
    }
}
