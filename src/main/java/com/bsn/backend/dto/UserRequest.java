package com.bsn.backend.dto;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRequest {

    private String fullName;

    private String email;

    private String phone;

    private String role;

    private String lookingFor;
}