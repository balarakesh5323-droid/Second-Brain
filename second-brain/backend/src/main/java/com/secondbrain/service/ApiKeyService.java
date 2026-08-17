package com.secondbrain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ApiKeyService {

    private final Map<String, ApiKeyInfo> apiKeys = new ConcurrentHashMap<>();

    public String generateApiKey(String agentName, String description) {
        String key = "sb_" + UUID.randomUUID().toString().replace("-", "");
        apiKeys.put(key, new ApiKeyInfo(agentName, description, System.currentTimeMillis()));
        log.info("Generated API key for agent: {}", agentName);
        return key;
    }

    public boolean validateApiKey(String key) {
        return apiKeys.containsKey(key);
    }

    public ApiKeyInfo getApiKeyInfo(String key) {
        return apiKeys.get(key);
    }

    public void revokeApiKey(String key) {
        apiKeys.remove(key);
        log.info("Revoked API key");
    }

    public record ApiKeyInfo(String agentName, String description, long createdAt) {}
}
