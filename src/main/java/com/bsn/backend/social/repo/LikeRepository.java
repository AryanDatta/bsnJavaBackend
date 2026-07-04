package com.bsn.backend.social.repo;

import com.bsn.backend.social.model.Like;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LikeRepository extends MongoRepository<Like, String> {

    Optional<Like> findBySubjectTypeAndSubjectIdAndUserId(String subjectType, String subjectId, String userId);

    boolean existsBySubjectTypeAndSubjectIdAndUserId(String subjectType, String subjectId, String userId);

    void deleteBySubjectTypeAndSubjectIdAndUserId(String subjectType, String subjectId, String userId);

}
