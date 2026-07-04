package com.bsn.backend.social.repo;

import com.bsn.backend.social.model.Challenge;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChallengeRepository extends MongoRepository<Challenge, String> {

    Optional<Challenge> findBySlug(String slug);

    List<Challenge> findByStatusOrderByStartAtDesc(String status, Pageable pageable);

    List<Challenge> findByStatusAndCityOrderByStartAtDesc(String status, String city, Pageable pageable);

}
