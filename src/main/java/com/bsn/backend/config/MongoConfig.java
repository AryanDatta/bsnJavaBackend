package com.bsn.backend.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

@Configuration
public class MongoConfig {

    @Value("${mongodb.uri:${MONGO_URI:}}")
    private String mongoUri;

    @Value("${mongodb.database:bsn}")
    private String databaseName;

    @Bean
    public MongoClient mongoClient() {
        String uri = System.getenv("MONGO_URI");

        if (uri == null || uri.isEmpty()) {
            // Only throw on Render (production)
            if (System.getenv("RENDER") != null) {
                throw new IllegalStateException("MONGO_URI is missing in Render environment variables");
            }

            // Local development fallback
            uri = "mongodb://localhost:27017/bsn";
            System.out.println("⚠️  No MONGO_URI found — falling back to local MongoDB");
            System.out.println("   → mongodb://localhost:27017/bsn");
        }

        return MongoClients.create(uri);
    }

    @Bean
    public MongoDatabaseFactory mongoDatabaseFactory(MongoClient mongoClient) {
        return new SimpleMongoClientDatabaseFactory(mongoClient, databaseName);
    }

    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory mongoDatabaseFactory) {
        return new MongoTemplate(mongoDatabaseFactory);
    }
}