package com.secondbrain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EmbeddingService {

    @Value("${ollama.base-url:http://192.168.0.114:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.embedding-model:nomic-embed-text}")
    private String embeddingModel;

    @Value("${ollama.embedding-dimensions:768}")
    private int embeddingDimensions;

    private final RestTemplate restTemplate = new RestTemplate();
    private boolean ollamaAvailable = false;

    @PostConstruct
    public void init() {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                ollamaBaseUrl + "/api/tags", Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                ollamaAvailable = true;
                log.info("Connected to Ollama at {} with model {}", ollamaBaseUrl, embeddingModel);
            }
        } catch (Exception e) {
            log.warn("Ollama not available at {}, using fallback embeddings: {}",
                ollamaBaseUrl, e.getMessage());
            ollamaAvailable = false;
        }
    }

    public float[] embed(String text) {
        if (ollamaAvailable) {
            try {
                return embedViaOllama(text);
            } catch (Exception e) {
                log.debug("Ollama embedding failed, falling back: {}", e.getMessage());
                return fallbackEmbed(text);
            }
        }
        return fallbackEmbed(text);
    }

    public List<float[]> embedBatch(List<String> texts) {
        return texts.stream()
            .map(this::embed)
            .collect(Collectors.toList());
    }

    private float[] embedViaOllama(String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("model", embeddingModel);
        body.put("prompt", text);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            ollamaBaseUrl + "/api/embeddings", request, Map.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            List<Double> embedding = (List<Double>) response.getBody().get("embedding");
            if (embedding != null) {
                float[] result = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    result[i] = embedding.get(i).floatValue();
                }
                return result;
            }
        }

        throw new RuntimeException("Failed to get embedding from Ollama");
    }

    private float[] fallbackEmbed(String text) {
        float[] embedding = new float[embeddingDimensions];
        int hash = text.hashCode();
        for (int i = 0; i < embeddingDimensions; i++) {
            embedding[i] = (float) Math.sin(hash * (i + 1) * 0.001);
        }
        return embedding;
    }

    public boolean isOllamaAvailable() {
        return ollamaAvailable;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public int getEmbeddingDimensions() {
        return embeddingDimensions;
    }
}
