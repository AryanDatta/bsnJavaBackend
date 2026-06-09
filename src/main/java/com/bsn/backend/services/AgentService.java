package com.bsn.backend.services;

import com.bsn.backend.dto.*;
import com.bsn.backend.exception.ResourceNotFoundException;
import com.bsn.backend.model.AgentCatalog;
import com.bsn.backend.model.AgentPurchase;
import com.bsn.backend.repository.AgentPurchaseRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentPurchaseRepository purchaseRepository;

    @Value("${razorpay.key-id:}")
    private String razorpayKeyId;

    @Value("${razorpay.key-secret:}")
    private String razorpayKeySecret;

    @Value("${bsn.agents.deploy-base-url:https://agents.bsn.ai}")
    private String deployBaseUrl;

    @Value("${paypal.me-link:}")
    private String paypalMeLink;

    @Value("${paypal.me-currency:}")
    private String paypalMeCurrency;

    /* ── Catalog (optionally enriched with the user's purchase state) ── */

    public List<AgentResponse> listAgents(String email) {
        Map<String, AgentPurchase> byAgent = (email == null || email.isBlank())
                ? Map.of()
                : purchaseRepository.findByUserEmail(email).stream()
                .collect(java.util.stream.Collectors.toMap(
                        AgentPurchase::getAgentId, p -> p, (a, b) -> b));

        return AgentCatalog.AGENTS.stream().map(a -> {
            AgentPurchase p = byAgent.get(a.id());
            String status = p == null ? "NONE" : p.getStatus();
            boolean owned = p != null && ("PAID".equals(status) || "DEPLOYED".equals(status));
            boolean deployed = "DEPLOYED".equals(status);
            return AgentResponse.builder()
                    .id(a.id())
                    .icon(a.icon())
                    .name(a.name())
                    .tag(a.tag())
                    .description(a.description())
                    .features(a.features())
                    .priceInr(a.priceInr())
                    .purchasable(a.purchasable())
                    .purchaseStatus(status)
                    .owned(owned)
                    .deployed(deployed)
                    .deployUrl(p == null ? null : p.getDeployUrl())
                    .build();
        }).toList();
    }

    public List<AgentPurchaseResponse> getPurchases(String email) {
        return purchaseRepository.findByUserEmail(email).stream()
                .map(this::mapToResponse)
                .toList();
    }

    /* ── Razorpay order creation ── */

    public AgentOrderResponse createOrder(CreateAgentOrderRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        AgentCatalog.CatalogAgent agent = AgentCatalog.findById(request.getAgentId())
                .orElseThrow(() -> new ResourceNotFoundException("unknown agent: " + request.getAgentId()));

        if (!agent.purchasable() || agent.priceInr() <= 0) {
            throw new IllegalArgumentException("agent is not available for purchase: " + agent.id());
        }
        ensureRazorpayConfigured();

        int amountPaise = agent.priceInr() * 100;

        try {
            RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "agent_" + agent.id() + "_" + System.currentTimeMillis());
            JSONObject notes = new JSONObject();
            notes.put("email", request.getEmail());
            notes.put("agentId", agent.id());
            orderRequest.put("notes", notes);

            Order order = client.orders.create(orderRequest);
            String orderId = order.get("id");

            // Upsert a purchase record in CREATED state
            AgentPurchase purchase = purchaseRepository
                    .findByUserEmailAndAgentId(request.getEmail(), agent.id())
                    .orElseGet(AgentPurchase::new);

            // already deployed/paid? keep it but still allow re-order (idempotent UX)
            purchase.setUserEmail(request.getEmail());
            purchase.setAgentId(agent.id());
            purchase.setAgentName(agent.name());
            purchase.setAmount(amountPaise);
            purchase.setCurrency("INR");
            purchase.setPaymentProvider("RAZORPAY");
            purchase.setRazorpayOrderId(orderId);
            if (purchase.getStatus() == null || "NONE".equals(purchase.getStatus())) {
                purchase.setStatus("CREATED");
            }
            if (purchase.getCreatedAt() == null) {
                purchase.setCreatedAt(LocalDateTime.now());
            }
            purchase.setUpdatedAt(LocalDateTime.now());
            // if not yet paid, reflect CREATED for this new order attempt
            if (!"PAID".equals(purchase.getStatus()) && !"DEPLOYED".equals(purchase.getStatus())) {
                purchase.setStatus("CREATED");
            }
            purchaseRepository.save(purchase);

            return AgentOrderResponse.builder()
                    .key(razorpayKeyId)
                    .orderId(orderId)
                    .amount(amountPaise)
                    .currency("INR")
                    .agentId(agent.id())
                    .agentName(agent.name())
                    .build();

        } catch (Exception e) {
            log.error("razorpay order creation failed", e);
            throw new IllegalArgumentException("could not create payment order: " + e.getMessage());
        }
    }

    /* ── Verify payment signature & mark PAID ── */

    public AgentPurchaseResponse verifyPayment(VerifyAgentPaymentRequest request) {
        if (request.getRazorpayOrderId() == null || request.getRazorpayPaymentId() == null
                || request.getRazorpaySignature() == null) {
            throw new IllegalArgumentException("razorpay_order_id, razorpay_payment_id and razorpay_signature are required");
        }
        ensureRazorpayConfigured();

        AgentPurchase purchase = purchaseRepository.findByRazorpayOrderId(request.getRazorpayOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("no purchase found for order: " + request.getRazorpayOrderId()));

        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", request.getRazorpayOrderId());
            options.put("razorpay_payment_id", request.getRazorpayPaymentId());
            options.put("razorpay_signature", request.getRazorpaySignature());

            boolean valid = Utils.verifyPaymentSignature(options, razorpayKeySecret);
            if (!valid) {
                throw new IllegalArgumentException("payment signature verification failed");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("signature verification error", e);
            throw new IllegalArgumentException("payment verification error: " + e.getMessage());
        }

        purchase.setRazorpayPaymentId(request.getRazorpayPaymentId());
        if (!"DEPLOYED".equals(purchase.getStatus())) {
            purchase.setStatus("PAID");
        }
        purchase.setUpdatedAt(LocalDateTime.now());
        return mapToResponse(purchaseRepository.save(purchase));
    }

    /* ── Deploy a purchased agent ── */

    public AgentPurchaseResponse deploy(DeployAgentRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        AgentPurchase purchase = purchaseRepository
                .findByUserEmailAndAgentId(request.getEmail(), request.getAgentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "no purchase found for this agent — please buy it first"));

        if (!"PAID".equals(purchase.getStatus()) && !"DEPLOYED".equals(purchase.getStatus())) {
            throw new IllegalArgumentException("agent must be purchased before it can be deployed");
        }

        if (purchase.getDeployUrl() == null) {
            String instanceId = UUID.randomUUID().toString().substring(0, 8);
            purchase.setDeployUrl(deployBaseUrl + "/" + purchase.getAgentId() + "/" + instanceId);
        }
        purchase.setStatus("DEPLOYED");
        purchase.setUpdatedAt(LocalDateTime.now());
        return mapToResponse(purchaseRepository.save(purchase));
    }

    /* ── PayPal.me (manual) checkout ── */

    /** Builds the PayPal.me pay URL for an agent and records a CREATED purchase. */
    public PaypalCheckoutResponse createPaypalCheckout(CreateAgentOrderRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        AgentCatalog.CatalogAgent agent = AgentCatalog.findById(request.getAgentId())
                .orElseThrow(() -> new ResourceNotFoundException("unknown agent: " + request.getAgentId()));
        if (!agent.purchasable() || agent.priceInr() <= 0) {
            throw new IllegalArgumentException("agent is not available for purchase: " + agent.id());
        }
        if (paypalMeLink == null || paypalMeLink.isBlank()) {
            throw new IllegalArgumentException("PayPal.me is not configured. Set the PAYPAL_ME_LINK environment variable.");
        }

        String base = paypalMeLink.endsWith("/") ? paypalMeLink.substring(0, paypalMeLink.length() - 1) : paypalMeLink;
        String suffix = (paypalMeCurrency == null || paypalMeCurrency.isBlank()) ? "" : paypalMeCurrency.trim().toUpperCase();
        String payUrl = base + "/" + agent.priceInr() + suffix;

        AgentPurchase purchase = purchaseRepository
                .findByUserEmailAndAgentId(request.getEmail(), agent.id())
                .orElseGet(AgentPurchase::new);
        purchase.setUserEmail(request.getEmail());
        purchase.setAgentId(agent.id());
        purchase.setAgentName(agent.name());
        purchase.setAmount(agent.priceInr() * 100);
        purchase.setCurrency(suffix.isBlank() ? "INR" : suffix);
        purchase.setPaymentProvider("PAYPAL_ME");
        if (purchase.getCreatedAt() == null) purchase.setCreatedAt(LocalDateTime.now());
        if (!"PAID".equals(purchase.getStatus()) && !"DEPLOYED".equals(purchase.getStatus())) {
            purchase.setStatus("CREATED");
        }
        purchase.setUpdatedAt(LocalDateTime.now());
        purchaseRepository.save(purchase);

        return PaypalCheckoutResponse.builder()
                .payUrl(payUrl)
                .priceInr(agent.priceInr())
                .agentId(agent.id())
                .agentName(agent.name())
                .build();
    }

    /**
     * Manually confirms a PayPal.me payment. PayPal.me cannot be verified
     * programmatically, so this marks the agent PAID on the user's confirmation.
     */
    public AgentPurchaseResponse confirmPaypal(CreateAgentOrderRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        AgentPurchase purchase = purchaseRepository
                .findByUserEmailAndAgentId(request.getEmail(), request.getAgentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "no PayPal checkout found — start the PayPal payment first"));

        purchase.setPaymentProvider("PAYPAL_ME");
        if (!"DEPLOYED".equals(purchase.getStatus())) {
            purchase.setStatus("PAID");
        }
        purchase.setUpdatedAt(LocalDateTime.now());
        return mapToResponse(purchaseRepository.save(purchase));
    }

    /* ── helpers ── */

    private void ensureRazorpayConfigured() {
        if (razorpayKeyId == null || razorpayKeyId.isBlank()
                || razorpayKeySecret == null || razorpayKeySecret.isBlank()) {
            throw new IllegalArgumentException(
                    "Razorpay is not configured. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET environment variables.");
        }
    }

    private AgentPurchaseResponse mapToResponse(AgentPurchase p) {
        return AgentPurchaseResponse.builder()
                .id(p.getId())
                .userEmail(p.getUserEmail())
                .agentId(p.getAgentId())
                .agentName(p.getAgentName())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .paymentProvider(p.getPaymentProvider())
                .status(p.getStatus())
                .razorpayOrderId(p.getRazorpayOrderId())
                .razorpayPaymentId(p.getRazorpayPaymentId())
                .deployUrl(p.getDeployUrl())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
