package com.bsn.backend.social.repo;

import com.bsn.backend.social.model.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    long countByUserIdAndReadFalse(String userId);

    long countByUserIdAndTypeAndActorIdAndCreatedAtAfter(String userId, String type, String actorId, Instant after);

}
