package com.bsn.backend.social.repo;

import com.bsn.backend.social.model.Follow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FollowRepository extends MongoRepository<Follow, String> {

    Optional<Follow> findByFollowerIdAndFolloweeId(String followerId, String followeeId);

    boolean existsByFollowerIdAndFolloweeId(String followerId, String followeeId);

    void deleteByFollowerIdAndFolloweeId(String followerId, String followeeId);

    List<Follow> findByFollowerId(String followerId);

    List<Follow> findByFollowerId(String followerId, Pageable pageable);

    List<Follow> findByFolloweeId(String followeeId, Pageable pageable);

    long countByFolloweeId(String followeeId);

}
