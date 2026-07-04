package com.bsn.backend.social.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/** Per-day member completion — drives "Arjun pending · 6h left". */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "squad_daily_status")
@CompoundIndex(name = "uniq_squad_day", def = "{'squadId':1,'localDate':1}", unique = true)
public class SquadDailyStatus {

    @Id
    private String id;

    private String squadId;
    private String localDate;
    private List<String> done;
    private List<String> pending;
    private Instant resolvedAt;
}
