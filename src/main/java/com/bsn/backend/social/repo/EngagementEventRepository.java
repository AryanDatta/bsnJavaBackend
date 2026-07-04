package com.bsn.backend.social.repo;

import com.bsn.backend.social.model.EngagementEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EngagementEventRepository extends MongoRepository<EngagementEvent, String> {

    long countByUserIdAndTypeAndCreatedAtAfter(String userId, String type, Instant after);

}
