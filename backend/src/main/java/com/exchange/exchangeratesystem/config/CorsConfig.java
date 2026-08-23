package com.exchange.exchangeratesystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the Angular dev server (a different origin — localhost:4200 vs the
 * backend's own localhost:8080) to call every {@code /api/**} endpoint from
 * the browser. Without this, every real frontend HTTP call fails with a CORS
 * error before ever reaching a controller — confirmed empirically during
 * T042. The allowed origin is externalized, per the same configurability
 * principle (FR-019/NFR-007) already applied to the frontend's own
 * environment files, so a reviewer running the frontend on a different port
 * never needs to edit backend source.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String frontendOrigin;

    public CorsConfig(@Value("${frontend.origin:http://localhost:4200}") String frontendOrigin) {
        this.frontendOrigin = frontendOrigin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(frontendOrigin);
    }
}
