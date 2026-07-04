package com.bsn.backend.social.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "story_views")
@CompoundIndex(name = "uniq_view", def = "{'storyId':1,'viewerId':1}", unique = true)
public class StoryView {

    @Id
    private String id;

    private String storyId;
    private String viewerId;
    private String authorId;

    @Indexed(expireAfter = "48h")
    private Instant viewedAt;
}
