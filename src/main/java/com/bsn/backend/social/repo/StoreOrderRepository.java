package com.bsn.backend.social.repo;

import com.bsn.backend.social.model.StoreOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StoreOrderRepository extends MongoRepository<StoreOrder, String> {

    List<StoreOrder> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

}
