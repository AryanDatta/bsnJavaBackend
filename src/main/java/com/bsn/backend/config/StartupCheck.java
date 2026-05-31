package com.bsn.backend.config;


import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class StartupCheck implements CommandLineRunner {

    @Override
    public void run(String... args) {
        String mongoUri = System.getenv("MONGO_URI");

        if (mongoUri == null || mongoUri.isBlank()) {
            System.out.println("MONGO_URI is missing");
        } else {
            System.out.println("MONGO_URI is present");
        }
    }
}