package com.bsn.backend.social.repo;

import com.bsn.backend.social.model.SeasonRank;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SeasonRankRepository extends MongoRepository<SeasonRank, String> {

    Optional<SeasonRank> findByUserIdAndSeasonId(String userId, String seasonId);

    List<SeasonRank> findByUserIdOrderBySeasonIdDesc(String userId);

    List<SeasonRank> findBySeasonIdAndCityOrderBySeasonPtsDesc(String seasonId, String city, Pageable pageable);

    long countBySeasonIdAndCityAndSeasonPtsGreaterThan(String seasonId, String city, long pts);

    List<SeasonRank> findBySeasonIdAndDecayActiveFalseAndLastEarnAtBefore(String seasonId, Instant before);

}
