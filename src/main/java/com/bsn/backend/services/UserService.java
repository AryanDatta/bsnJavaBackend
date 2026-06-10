package com.bsn.backend.services;

import com.bsn.backend.dto.LoginRequest;
import com.bsn.backend.dto.UserRequest;
import com.bsn.backend.dto.UserResponse;
import com.bsn.backend.exception.ResourceNotFoundException;
import com.bsn.backend.model.User;
import com.bsn.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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