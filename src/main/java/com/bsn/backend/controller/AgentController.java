package com.bsn.backend.controller;

import com.bsn.backend.dto.*;
import com.bsn.backend.services.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "ai agent marketplace", description = "browse, buy (razorpay) and deploy bsn ai agents")
@RestController
@CrossOrigin
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @Operation(summary = "list marketplace agents (optionally enriched with a user's purchase state)")
    @GetMapping
    public ResponseEntity<List<AgentResponse>> listAgents(@RequestParam(required = false) String email) {
        return ResponseEntity.ok(agentService.listAgents(email));
    }

    @Operation(summary = "list a user's agent purchases")
    @GetMapping("/purchases/{email}")
    public ResponseEntity<List<AgentPurchaseResponse>> getPurchases(@PathVariable String email) {
        return ResponseEntity.ok(agentService.getPurchases(email));
    }

    @Operation(summary = "create a razorpay order to buy an agent")
    @PostMapping("/order")
    public ResponseEntity<AgentOrderResponse> createOrder(@RequestBody CreateAgentOrderRequest request) {
        AgentOrderResponse response = agentService.createOrder(request);
        log.info("agent order created: {} for {}", response.getOrderId(), request.getEmail());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "verify a razorpay payment and mark the agent as purchased")
    @PostMapping("/verify")
    public ResponseEntity<AgentPurchaseResponse> verifyPayment(@RequestBody VerifyAgentPaymentRequest request) {
        return ResponseEntity.ok(agentService.verifyPayment(request));
    }

    @Operation(summary = "start a PayPal.me checkout — returns the pre-filled pay link")
    @PostMapping("/paypal/checkout")
    public ResponseEntity<PaypalCheckoutResponse> paypalCheckout(@RequestBody CreateAgentOrderRequest request) {
        PaypalCheckoutResponse response = agentService.createPaypalCheckout(request);
        log.info("paypal.me checkout for {} -> {}", request.getEmail(), response.getPayUrl());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "confirm a PayPal.me payment (manual) and mark the agent purchased")
    @PostMapping("/paypal/confirm")
    public ResponseEntity<AgentPurchaseResponse> paypalConfirm(@RequestBody CreateAgentOrderRequest request) {
        return ResponseEntity.ok(agentService.confirmPaypal(request));
    }

    @Operation(summary = "deploy a purchased agent")
    @PostMapping("/deploy")
    public ResponseEntity<AgentPurchaseResponse> deploy(@RequestBody DeployAgentRequest request) {
        AgentPurchaseResponse response = agentService.deploy(request);
        log.info("agent deployed: {} for {} -> {}", response.getAgentId(), response.getUserEmail(), response.getDeployUrl());
        return ResponseEntity.ok(response);
    }
}
