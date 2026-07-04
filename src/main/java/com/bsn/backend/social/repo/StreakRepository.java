package com.bsn.backend.social.repo;

import com.bsn.backend.social.model.Streak;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StreakRepository extends MongoRepository<Streak, String> {

    List<Streak> findByGraceDeadlineAtBefore(Instant now);

}
