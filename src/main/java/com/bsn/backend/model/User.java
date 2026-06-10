package com.bsn.backend.model;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "users")
public class User {

    @Id
    private String id;

    private String fullName;

    private String email;

    private String phone;

    private String role;

    private String lookingFor;

    private String password;

    /* ── Password reset (forgot password OTP) ── */
    private String resetOtpHash;
    private LocalDateTime resetOtpExpiry;
    private Integer resetOtpAttempts;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}