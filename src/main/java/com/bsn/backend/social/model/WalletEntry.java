package com.bsn.backend.social.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Append-only ledger — balance is a fold; nothing mutates balances directly (§2.2).
 * "Points move only when you record."
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "wallet_ledger")
@CompoundIndex(name = "user_time", def = "{'userId':1,'createdAt':-1}")
@CompoundIndex(name = "maturing", def = "{'matured':1,'maturesAt':1}")
public class WalletEntry {

    @Id
    private String id;

    private String userId;
    private long delta;             // negative for spends
    private long balanceAfter;

    private String kind;            // EARN_VERIFIED | EARN_CHALLENGE | EARN_BONUS | SPEND_COSMETIC | SPEND_REWARD | PENALTY_QUIT
    private String refType;         // verification | challenge | store_order
    private String refId;

    private Instant maturesAt;      // challenge payouts vest at challenge end
    private boolean matured;

    @Indexed(unique = true, sparse = true)
    private String idempotencyKey;  // e.g. "verif:<id>" — prevents double-award

    private Instant createdAt;
}
