package com.bsn.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/** Sent to the front-end so it can open Razorpay checkout. */
@Getter
@Setter
@Builder
public class AgentOrderResponse {
    private String key;          // Razorpay key id (public)
    private String orderId;      // Razorpay order id
    private int amount;          // in paise
    private String currency;
    private String agentId;
    private String agentName;
}
