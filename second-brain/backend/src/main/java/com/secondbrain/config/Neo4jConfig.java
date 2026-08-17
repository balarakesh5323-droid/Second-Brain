package com.secondbrain.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "neo4j")
@Data
public class Neo4jConfig {
    private String uri = "bolt://localhost:7687";
    private String username = "neo4j";
    private String password = "password";
}
