package com.bsn.backend.social.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "hashtag_stats")
public class HashtagStat {

    @Id
    private String tag;

    private long postCount;
    private long last24hCount;

    @Indexed
    private double trendScore;

    private Instant updatedAt;
}
