package com.bsn.backend.social.repo;

import com.bsn.backend.social.model.SquadDailyStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SquadDailyStatusRepository extends MongoRepository<SquadDailyStatus, String> {

    Optional<SquadDailyStatus> findBySquadIdAndLocalDate(String squadId, String localDate);

}
