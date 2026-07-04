package com.bsn.backend.social.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables @Async feed fan-out and the scheduled jobs (§7.3). */
@Configuration
@EnableAsync
@EnableScheduling
public class SocialAsyncConfig {
}
