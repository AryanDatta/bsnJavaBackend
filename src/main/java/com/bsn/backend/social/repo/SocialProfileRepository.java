package com.bsn.backend.social.repo;

import com.bsn.backend.social.model.SocialProfile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SocialProfileRepository extends MongoRepository<SocialProfile, String> {

    Optional<SocialProfile> findByHandle(String handle);

    Optional<SocialProfile> findByShareSlug(String shareSlug);

    List<SocialProfile> findByHandleStartingWithIgnoreCase(String prefix, Pageable pageable);

}
