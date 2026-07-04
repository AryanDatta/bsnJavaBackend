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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "challenge_participants")
@CompoundIndex(name = "uniq_member", def = "{'challengeId':1,'userId':1}", unique = true)
@CompoundIndex(name = "user_state", def = "{'userId':1,'state':1}")
@CompoundIndex(name = "challenge_pts", def = "{'challengeId':1,'pointsEarned':-1}")
public class ChallengeParticipant {

    @Id
    private String id;

    private String challengeId;
    private String challengeSlug;
    private String userId;
    private Instant joinedAt;

    private int day;                       // furthest day credited
    private List<Integer> verifiedDays;
    private int freezesLeft;
    private String lastCreditLocalDate;    // one credit per local day

    private List<String> friendsJoined;
    private boolean friendBonusPaid;

    private String state;                  // ACTIVE | FINISHED | QUIT | FAILED
    private long pointsEarned;
    private Instant finishedAt;
}
