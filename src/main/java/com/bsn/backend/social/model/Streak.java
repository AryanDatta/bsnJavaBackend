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
import java.util.Map;

/** Per-user streak state; heatmap = month -> day-bit string ("110111..."). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "streaks")
public class Streak {

    @Id
    private String userId;

    private int current;
    private int longest;
    private String lastVerifiedLocalDate;   // "2026-07-04"
    private String tz;

    private int freezesAvailable;
    private List<FreezeUse> freezesUsed;

    private Map<String, String> heatmap;    // "2026-07" -> "1111011..."

    @Indexed
    private Instant graceDeadlineAt;        // streak-tick job scans this

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FreezeUse {
        private String date;
        private String source;   // e.g. CHALLENGE_FITZONE-30
    }
}
