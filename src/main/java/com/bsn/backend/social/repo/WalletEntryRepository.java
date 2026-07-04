package com.bsn.backend.social.repo;

import com.bsn.backend.social.model.WalletEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WalletEntryRepository extends MongoRepository<WalletEntry, String> {

    Optional<WalletEntry> findTopByUserIdOrderByCreatedAtDesc(String userId);

    List<WalletEntry> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    List<WalletEntry> findByUserIdAndMaturedFalse(String userId);

    List<WalletEntry> findByMaturedFalseAndMaturesAtBefore(Instant now);

    boolean existsByIdempotencyKey(String idempotencyKey);

}
