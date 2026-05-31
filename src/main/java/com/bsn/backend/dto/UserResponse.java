package com.bsn.backend.dto;


import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class UserResponse {

    private String id;

    private String fullName;

    private String email;

    private String phone;

    private String role;

    private String lookingFor;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}