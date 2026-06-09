package com.bsn.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateAgentOrderRequest {
    private String email;
    private String agentId;
}
