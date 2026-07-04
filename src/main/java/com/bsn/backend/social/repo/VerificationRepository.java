package com.bsn.backend.social.repo;

import com.bsn.backend.social.model.Verification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VerificationRepository extends MongoRepository<Verification, String> {

    boolean existsByUserIdAndLocalDateAndStatus(String userId, String localDate, String status);

    Optional<Verification> findByPostId(String postId);

    List<Verification> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

}
