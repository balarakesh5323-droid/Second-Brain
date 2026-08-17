package com.secondbrain.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "qdrant")
@Data
public class QdrantConfig {
    private String host = "localhost";
    private int port = 6334;
    private int grpcPort = 6334;
}
