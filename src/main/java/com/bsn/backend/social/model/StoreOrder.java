package com.bsn.backend.social.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "store_orders")
@CompoundIndex(name = "user_time", def = "{'userId':1,'createdAt':-1}")
public class StoreOrder {

    @Id
    private String id;

    private String userId;
    private String itemId;
    private String itemName;
    private String aisle;
    private long pricePts;

    private String state;            // RESERVED | KYC_PENDING | FULFILLED | CANCELLED
    private String ledgerEntryId;    // the SPEND entry
    private String fulfillmentType;  // VOUCHER | SHIPMENT | COSMETIC_UNLOCK
    private String fulfillmentDetail;

    private Instant createdAt;
}
