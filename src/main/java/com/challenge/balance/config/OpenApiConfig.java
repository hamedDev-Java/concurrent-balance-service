package com.challenge.balance.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI balanceServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Concurrent Balance Service API")
                        .version("1.0.0")
                        .description("REST API documentation for Concurrent Balance Service. Supports atomic transfers, Pessimistic Locking, and idempotent financial operations.")
                        .contact(new Contact().name("Senior Backend Engineering Team"))
                        .license(new License().name("Apache 2.0")));
    }
}
