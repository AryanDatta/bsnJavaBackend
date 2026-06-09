package com.bsn.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** A marketplace agent enriched with the current user's purchase/deploy state. */
@Getter
@Setter
@Builder
public class AgentResponse {

    private String id;
    private String icon;
    private String name;
    private String tag;
    private String description;
    private List<String> features;

    /** price in INR (rupees); 0 means not for sale */
    private int priceInr;
    private boolean purchasable;

    /** NONE, CREATED, PAID, DEPLOYED — relative to the requesting user */
    private String purchaseStatus;
    private boolean owned;
    private boolean deployed;
    private String deployUrl;
}
