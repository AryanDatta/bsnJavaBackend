package com.bsn.backend.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * A record of a user buying (and deploying) one AI agent from the marketplace.
 * Status flow: CREATED -> PAID -> DEPLOYED.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "agent_purchases")
@CompoundIndex(name = "user_agent_idx", def = "{'userEmail': 1, 'agentId': 1}")
public class AgentPurchase {

    @Id
    private String id;

    private String userEmail;

    private String agentId;

    private String agentName;

    /** amount in paise (Razorpay's smallest currency unit) */
    private int amount;

    private String currency;

    /** RAZORPAY or PAYPAL_ME */
    private String paymentProvider;

    /** CREATED, PAID, DEPLOYED */
    private String status;

    private String razorpayOrderId;

    private String razorpayPaymentId;

    /** populated once the agent is deployed */
    private String deployUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
