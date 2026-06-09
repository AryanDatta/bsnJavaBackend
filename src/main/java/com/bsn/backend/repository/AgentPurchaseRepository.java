package com.bsn.backend.repository;

import com.bsn.backend.model.AgentPurchase;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AgentPurchaseRepository extends MongoRepository<AgentPurchase, String> {

    List<AgentPurchase> findByUserEmail(String userEmail);

    Optional<AgentPurchase> findByUserEmailAndAgentId(String userEmail, String agentId);

    Optional<AgentPurchase> findByRazorpayOrderId(String razorpayOrderId);
}
