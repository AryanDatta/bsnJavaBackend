package com.bsn.backend.social.repo;

import com.bsn.backend.social.model.Story;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StoryRepository extends MongoRepository<Story, String> {

    List<Story> findByAuthorIdInAndExpiresAtAfterOrderByCreatedAtDesc(List<String> authorIds, Instant now);

    List<Story> findByAuthorIdAndExpiresAtAfterOrderByCreatedAtAsc(String authorId, Instant now);

}
