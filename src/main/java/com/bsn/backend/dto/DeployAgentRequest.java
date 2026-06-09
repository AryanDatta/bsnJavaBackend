package com.bsn.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeployAgentRequest {
    private String email;
    private String agentId;
}
