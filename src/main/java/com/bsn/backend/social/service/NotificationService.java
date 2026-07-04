package com.bsn.backend.social.service;

import com.bsn.backend.social.common.CursorUtil;
import com.bsn.backend.social.model.Notification;
import com.bsn.backend.social.repo.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifications;
    private final MongoTemplate mongo;

    public void push(String userId, String type, String actorId, String refType, String refId, String text) {
        if (userId == null || userId.equals(actorId)) {
            return; // never notify yourself
        }
        notifications.save(Notification.builder()
                .userId(userId).type(type).actorId(actorId)
                .refType(refType).refId(refId).text(text)
                .read(false).createdAt(Instant.now())
                .build());
    }

    /** Rate-limited variant used for nudges (3/day per target per actor). */
    public boolean pushLimited(String userId, String type, String actorId, String refType, String refId,
                               String text, int maxPerDay) {
        Instant dayAgo = Instant.now().minusSeconds(86400);
        if (notifications.countByUserIdAndTypeAndActorIdAndCreatedAtAfter(userId, type, actorId, dayAgo) >= maxPerDay) {
            return false;
        }
        push(userId, type, actorId, refType, refId, text);
        return true;
    }

    public Map<String, Object> list(String userId, String cursor, Integer limit) {
        int lim = CursorUtil.clampLimit(limit);
        CursorUtil.Cursor c = CursorUtil.decode(cursor);
        Query q = new Query(Criteria.where("userId").is(userId));
        if (c != null) {
            q.addCriteria(Criteria.where("createdAt").lt(Instant.ofEpochMilli(c.millis())));
        }
        q.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
                .limit(lim);
        List<Notification> page = mongo.find(q, Notification.class);
        String next = page.size() == lim
                ? CursorUtil.encode(page.get(page.size() - 1).getCreatedAt().toEpochMilli(), "")
                : null;
        return Map.of(
                "items", page,
                "unread", notifications.countByUserIdAndReadFalse(userId),
                "nextCursor", next == null ? "" : next
        );
    }

    public void markAllRead(String userId) {
        mongo.updateMulti(
                new Query(Criteria.where("userId").is(userId).and("read").is(false)),
                new Update().set("read", true),
                Notification.class);
    }
}
