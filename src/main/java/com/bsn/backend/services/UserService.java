package com.bsn.backend.services;

import com.bsn.backend.dto.LoginRequest;
import com.bsn.backend.dto.UserRequest;
import com.bsn.backend.dto.UserResponse;
import com.bsn.backend.exception.ResourceNotFoundException;
import com.bsn.backend.model.User;
import com.bsn.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final int OTP_VALID_MINUTES = 10;
    private static final int OTP_MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    public UserResponse createUser(UserRequest request) {
        validateUserRequest(request);

        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("user already exists with email: " + email);
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(email)
                .phone(request.getPhone())
                .role(request.getRole())
                .lookingFor(request.getLookingFor())
                .password(passwordEncoder.encode(request.getPassword()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        try {
            User savedUser = userRepository.save(user);
            return mapToResponse(savedUser);
        } catch (DuplicateKeyException e) {
            // Unique index caught a race (e.g. double-click / parallel requests)
            throw new IllegalArgumentException("user already exists with email: " + email);
        }
    }

    public UserResponse login(LoginRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("email is required");
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("password is required");
        }

        User user = userRepository.findByEmail(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new IllegalArgumentException("invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("invalid email or password");
        }

        return mapToResponse(user);
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public UserResponse getUserById(String id) {
        User user = findUserOrThrow(id);
        return mapToResponse(user);
    }

    public UserResponse updateUser(String id, UserRequest request) {
        User user = findUserOrThrow(id);

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }

        if (request.getEmail() != null) {
            String newEmail = normalizeEmail(request.getEmail());
            if (!newEmail.equals(user.getEmail()) && userRepository.existsByEmail(newEmail)) {
                throw new IllegalArgumentException("user already exists with email: " + newEmail);
            }
            user.setEmail(newEmail);
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }

        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }

        if (request.getLookingFor() != null) {
            user.setLookingFor(request.getLookingFor());
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        user.setUpdatedAt(LocalDateTime.now());

        User updatedUser = userRepository.save(user);
        return mapToResponse(updatedUser);
    }

    public void deleteUser(String id) {
        User user = findUserOrThrow(id);
        userRepository.delete(user);
    }

    /* ── FORGOT PASSWORD (OTP) ──────────────────────────────────────── */

    /**
     * Generates a 6-digit OTP, stores only its hash (10-min expiry),
     * and emails the code. Silently succeeds for unknown emails so
     * attackers cannot probe which addresses are registered.
     */
    public void forgotPassword(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }

        String email = normalizeEmail(rawEmail);
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.info("forgot-password requested for unknown email (no action): {}", email);
            return;
        }

        User user = userOpt.get();
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));

        user.setResetOtpHash(passwordEncoder.encode(otp));
        user.setResetOtpExpiry(LocalDateTime.now().plusMinutes(OTP_VALID_MINUTES));
        user.setResetOtpAttempts(0);
        userRepository.save(user);

        sendOtpEmail(email, user.getFullName(), otp);
    }

    public void resetPassword(String rawEmail, String otp, String newPassword) {
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        if (otp == null || otp.isBlank()) {
            throw new IllegalArgumentException("otp is required");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("new password must be at least 6 characters");
        }

        User user = userRepository.findByEmail(normalizeEmail(rawEmail))
                .orElseThrow(() -> new IllegalArgumentException("invalid or expired code"));

        if (user.getResetOtpHash() == null || user.getResetOtpExpiry() == null
                || user.getResetOtpExpiry().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("invalid or expired code");
        }

        int attempts = user.getResetOtpAttempts() == null ? 0 : user.getResetOtpAttempts();
        if (attempts >= OTP_MAX_ATTEMPTS) {
            clearOtp(user);
            userRepository.save(user);
            throw new IllegalArgumentException("too many attempts - request a new code");
        }

        if (!passwordEncoder.matches(otp.trim(), user.getResetOtpHash())) {
            user.setResetOtpAttempts(attempts + 1);
            userRepository.save(user);
            throw new IllegalArgumentException("invalid or expired code");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        clearOtp(user);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    private void clearOtp(User user) {
        user.setResetOtpHash(null);
        user.setResetOtpExpiry(null);
        user.setResetOtpAttempts(null);
    }

    private void sendOtpEmail(String email, String fullName, String otp) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.error("JavaMailSender not configured - cannot send reset OTP");
            throw new IllegalArgumentException("email service is not configured - please contact support");
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(email);
            msg.setSubject("BSN password reset code: " + otp);
            msg.setText("Hi " + (fullName == null ? "there" : fullName) + ",\n\n"
                    + "Your BSN password reset code is:\n\n"
                    + "    " + otp + "\n\n"
                    + "It expires in " + OTP_VALID_MINUTES + " minutes. "
                    + "If you didn't request this, you can safely ignore this email.\n\n"
                    + "— BSN · Bandna Shri Nika");
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send reset OTP email: {}", e.getMessage());
            throw new IllegalArgumentException("could not send reset email - please try again later");
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private User findUserOrThrow(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("user not found with id: " + id));
    }

    private void validateUserRequest(UserRequest request) {
        if (request.getFullName() == null || request.getFullName().isBlank()) {
            throw new IllegalArgumentException("fullName is required");
        }

        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("email is required");
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("password is required");
        }
    }

    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .lookingFor(user.getLookingFor())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}