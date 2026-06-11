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

        // ── Mail (forgot-password OTP) diagnostics ──
        String mailUser = System.getenv("MAIL_USERNAME");
        String mailPass = System.getenv("MAIL_PASSWORD");

        if (mailUser == null || mailUser.isBlank()) {
            System.out.println("MAIL_USERNAME is missing - forgot-password emails will fail");
        } else {
            System.out.println("MAIL_USERNAME is present: " + mailUser);
        }

        if (mailPass == null || mailPass.isBlank()) {
            System.out.println("MAIL_PASSWORD is missing - forgot-password emails will fail");
        } else if (mailPass.contains(" ")) {
            System.out.println("WARNING: MAIL_PASSWORD contains spaces - remove them! "
                    + "Gmail app passwords must be entered without spaces (16 characters).");
        } else {
            System.out.println("MAIL_PASSWORD is present (" + mailPass.length() + " chars)");
        }
    }
}