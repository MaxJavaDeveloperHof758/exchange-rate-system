package com.exchange.exchangeratesystem.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI exchangeRateSystemOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Exchange Rate Management System API")
                        .description("Spread-adjusted exchange rate calculator, historical rate "
                                + "and usage analytics, and AI-generated trend insight endpoints.")
                        .version("v1"));
    }
}
