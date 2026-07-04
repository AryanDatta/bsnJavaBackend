package com.bsn.backend.social.repo;

import com.bsn.backend.social.model.ChallengeParticipant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChallengeParticipantRepository extends MongoRepository<ChallengeParticipant, String> {

    Optional<ChallengeParticipant> findByChallengeIdAndUserId(String challengeId, String userId);

    List<ChallengeParticipant> findByUserIdAndState(String userId, String state);

    List<ChallengeParticipant> findByUserId(String userId);

    List<ChallengeParticipant> findByChallengeIdOrderByPointsEarnedDesc(String challengeId, Pageable pageable);

}
