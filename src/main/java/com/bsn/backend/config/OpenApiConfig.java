package com.bsn.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bsnOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("bsn backend api")
                        .description("basic user crud apis. oauth will be added in the next phase.")
                        .version("v1.0.0"));
    }
}