package com.cisternino.config;

// CORS is now configured in SecurityConfig.java
// This class is kept empty to avoid conflicts.
// Previously: allowed all origins on /api/**

import org.springframework.context.annotation.Configuration;

@Configuration
public class CorsConfig {
    // CORS handling moved to SecurityConfig
}
