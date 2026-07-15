package de.uni_passau.fim.se2.infrastructure.persistence.config;

import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.*;

import javax.sql.*;

@Configuration
public class JdbcConfig {

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}