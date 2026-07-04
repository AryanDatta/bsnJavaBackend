package com.bsn.backend.social.repo;

import com.bsn.backend.social.model.FeedEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FeedEntryRepository extends MongoRepository<FeedEntry, String> {

    boolean existsByOwnerIdAndPostId(String ownerId, String postId);

    void deleteByPostId(String postId);

    List<FeedEntry> findByOwnerIdOrderByCreatedAtDesc(String ownerId, Pageable pageable);

    List<FeedEntry> findByOwnerIdAndCreatedAtBeforeOrderByCreatedAtDesc(String ownerId, Instant before, Pageable pageable);

}
