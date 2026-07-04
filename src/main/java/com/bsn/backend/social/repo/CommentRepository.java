package com.bsn.backend.social.repo;

import com.bsn.backend.social.model.Comment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CommentRepository extends MongoRepository<Comment, String> {

    List<Comment> findByPostIdAndStatusOrderByCreatedAtDesc(String postId, String status, Pageable pageable);

}
