package com.bsn.backend.social.service;

import com.bsn.backend.social.model.WalletEntry;
import com.bsn.backend.social.repo.WalletEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The ONLY writer of points (§8.2). Append-only ledger; balance is a fold.
 * Every entry carries an idempotency key — double-award is structurally impossible.
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletEntryRepository ledger;

    /**
     * Appends a ledger entry. Returns null if the idempotency key was already used.
     * maturesAt != null → points vest later (challenge payouts, §5.4).
     */
    public synchronized WalletEntry append(String userId, long delta, String kind, String refType,
                                           String refId, String idempotencyKey, Instant maturesAt) {
        if (idempotencyKey != null && ledger.existsByIdempotencyKey(idempotencyKey)) {
            return null;
        }
        long balance = balance(userId) + delta;
        if (balance < 0) {
            throw new IllegalArgumentException("insufficient points");
        }
        try {
            return ledger.save(WalletEntry.builder()
                    .userId(userId).delta(delta).balanceAfter(balance)
                    .kind(kind).refType(refType).refId(refId)
                    .maturesAt(maturesAt).matured(maturesAt == null)
                    .idempotencyKey(idempotencyKey)
                    .createdAt(Instant.now())
                    .build());
        } catch (DuplicateKeyException e) {
            return null; // concurrent duplicate — idempotent
        }
    }

    public long balance(String userId) {
        return ledger.findTopByUserIdOrderByCreatedAtDesc(userId)
                .map(WalletEntry::getBalanceAfter).orElse(0L);
    }

    /** Spendable = balance minus not-yet-vested earnings ("+180 maturing in 4 days"). */
    public long availableBalance(String userId) {
        long maturing = ledger.findByUserIdAndMaturedFalse(userId).stream()
                .mapToLong(WalletEntry::getDelta).sum();
        return balance(userId) - Math.max(0, maturing);
    }

    public Map<String, Object> wallet(String userId) {
        List<WalletEntry> unmatured = ledger.findByUserIdAndMaturedFalse(userId);
        long maturing = unmatured.stream().mapToLong(WalletEntry::getDelta).sum();
        Instant nextMaturity = unmatured.stream().map(WalletEntry::getMaturesAt)
                .filter(java.util.Objects::nonNull).min(Instant::compareTo).orElse(null);
        return new java.util.HashMap<>(Map.of(
                "balance", balance(userId),
                "available", availableBalance(userId),
                "maturing", Math.max(0, maturing),
                "nextMaturityAt", nextMaturity == null ? "" : nextMaturity.toString()
        ));
    }

    public List<WalletEntry> statement(String userId, int page, int size) {
        return ledger.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, Math.min(size, 50)));
    }

    /** Hourly job: vest matured entries (§7.3 PointsMaturity). */
    public int matureDuePoints() {
        List<WalletEntry> due = ledger.findByMaturedFalseAndMaturesAtBefore(Instant.now());
        due.forEach(e -> e.setMatured(true));
        if (!due.isEmpty()) {
            ledger.saveAll(due);
        }
        return due.size();
    }
}
