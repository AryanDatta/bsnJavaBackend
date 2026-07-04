package com.bsn.backend.social.repo;

import com.bsn.backend.social.model.StoryView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StoryViewRepository extends MongoRepository<StoryView, String> {

    boolean existsByStoryIdAndViewerId(String storyId, String viewerId);

    List<StoryView> findByViewerIdAndAuthorIdIn(String viewerId, List<String> authorIds);

}
