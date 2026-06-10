package com.bsn.backend.config;

import com.bsn.backend.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One-time, idempotent migration that runs on every startup:
 * 1. Normalizes all user emails (trim + lowercase).
 * 2. Removes duplicate records per email, keeping the OLDEST account.
 * 3. Ensures a unique index on users.email so duplicates can never
 *    be inserted again — even by concurrent requests.
 */
@Component
@RequiredArgsConstructor
public class UserEmailUniquenessMigration implements CommandLineRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) {
        try {
            List<User> users = mongoTemplate.findAll(User.class);

            // Oldest first, so the original account wins
            users.sort(Comparator.comparing(User::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())));

            Set<String> seen = new HashSet<>();
            List<User> duplicates = new ArrayList<>();

            for (User u : users) {
                String normalized = u.getEmail() == null ? "" : u.getEmail().trim().toLowerCase();

                if (!seen.add(normalized)) {
                    duplicates.add(u);
                    continue;
                }

                // Persist normalization for kept records if it changed
                if (!normalized.equals(u.getEmail())) {
                    u.setEmail(normalized);
                    mongoTemplate.save(u);
                }
            }

            for (User dup : duplicates) {
                mongoTemplate.remove(dup);
            }

            if (!duplicates.isEmpty()) {
                System.out.println("✔ Removed " + duplicates.size() + " duplicate user record(s)");
            }

            mongoTemplate.indexOps(User.class)
                    .createIndex(new Index().on("email", Sort.Direction.ASC).unique());
            System.out.println("✔ Unique index on users.email ensured");

        } catch (Exception e) {
            // Never block app startup; log and continue
            System.err.println("⚠ User email uniqueness migration failed: " + e.getMessage());
        }
    }
}
