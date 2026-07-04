package com.bsn.backend.social.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/** "Two aisles. Both priced in sweat." Rank opens the shelf; effort pays for it. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "store_items")
@CompoundIndex(name = "aisle_status", def = "{'aisle':1,'status':1}")
public class StoreItem {

    @Id
    private String id;

    private String aisle;        // REWARD (real world) | COSMETIC (in app)
    private String name;
    private String sub;
    private long pricePts;
    private String tag;

    private String minTier;      // null or Tier name gate (e.g. "IMMORTAL ONLY" cosmetics)
    private boolean kycRequired; // rewards require one-time KYC before first redemption

    private Integer stock;       // null = unlimited
    private String status;       // LIVE | HIDDEN
}
