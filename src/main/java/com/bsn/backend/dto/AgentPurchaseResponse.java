package com.bsn.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class AgentPurchaseResponse {
    private String id;
    private String userEmail;
    private String agentId;
    private String agentName;
    private int amount;
    private String currency;
    private String paymentProvider;
    private String status;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String deployUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
