package com.bsn.backend.controller;

import com.bsn.backend.dto.ForgotPasswordRequest;
import com.bsn.backend.dto.LoginRequest;
import com.bsn.backend.dto.ResetPasswordRequest;
import com.bsn.backend.dto.UserRequest;
import com.bsn.backend.dto.UserResponse;
import com.bsn.backend.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "user crud apis", description = "basic user crud apis for bsn")
@RestController
@CrossOrigin
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "create user / signup")
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@RequestBody UserRequest request) {
        UserResponse response = userService.createUser(request);
        log.info("user created: {}", response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "login user")
    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@RequestBody LoginRequest request) {
        UserResponse response = userService.login(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "request password reset OTP via email")
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        userService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(Map.of(
                "message", "if an account exists for this email, a reset code has been sent"
        ));
    }

    @Operation(summary = "reset password using emailed OTP")
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request.getEmail(), request.getOtp(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "password updated successfully"));
    }

    @Operation(summary = "get all users")
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @Operation(summary = "get user by id")
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @Operation(summary = "update user")
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable String id,
            @RequestBody UserRequest request
    ) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    @Operation(summary = "delete user")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}