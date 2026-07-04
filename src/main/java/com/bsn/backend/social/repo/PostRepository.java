package com.bsn.backend.social.repo;

import com.bsn.backend.social.model.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends MongoRepository<Post, String> {

    List<Post> findByAuthorIdAndStatusOrderByCreatedAtDesc(String authorId, String status, Pageable pageable);

    List<Post> findByChallengeIdAndStatusOrderByCreatedAtDesc(String challengeId, String status, Pageable pageable);

    List<Post> findByAuthorIdInAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(List<String> authorIds, String status, Instant after, Pageable pageable);

    long countByAuthorIdAndStatus(String authorId, String status);

}
