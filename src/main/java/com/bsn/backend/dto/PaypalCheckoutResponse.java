package com.bsn.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/** Returned to the front-end so it can open the PayPal.me link pre-filled with the amount. */
@Getter
@Setter
@Builder
public class PaypalCheckoutResponse {
    private String payUrl;     // e.g. https://paypal.me/Datta924/4999INR
    private int priceInr;      // displayed price in rupees
    private String agentId;
    private String agentName;
}
