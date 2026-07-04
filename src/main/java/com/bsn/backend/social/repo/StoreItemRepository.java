package com.bsn.backend.social.repo;

import com.bsn.backend.social.model.StoreItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StoreItemRepository extends MongoRepository<StoreItem, String> {

    List<StoreItem> findByAisleAndStatus(String aisle, String status);

    List<StoreItem> findByStatus(String status);

}
