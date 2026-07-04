package com.bsn.backend.social.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/** Accountability group ("Subah 5 Baje") — squad streak survives only if everyone records. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "squads")
public class Squad {

    public static final int MAX_MEMBERS = 8;

    @Id
    private String id;

    private String name;
    private String ownerId;

    @Indexed
    private List<String> memberIds;

    @Indexed(unique = true, sparse = true)
    private String inviteCode;

    private int streakCurrent;
    private String lastCompleteLocalDate;
    private String rule;             // ALL_MUST_RECORD
    private String theme;
    private List<String> themeUnlocks;

    private Instant createdAt;
}
