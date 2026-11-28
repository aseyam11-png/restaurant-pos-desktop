package com.restaurantpos.localapi.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private static final List<String> ALLOWED_ORIGINS = List.of(
        "tauri://localhost",
        "http://tauri.localhost",
        "https://tauri.localhost",
        "http://127.0.0.1:1420",
        "http://localhost:1420"
    );

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins(ALLOWED_ORIGINS.toArray(String[]::new))
            .allowedMethods("GET", "POST", "PATCH", "OPTIONS")
            .allowedHeaders("Content-Type", "X-POS-Local-Token", "Idempotency-Key")
            .maxAge(600);
    }
}