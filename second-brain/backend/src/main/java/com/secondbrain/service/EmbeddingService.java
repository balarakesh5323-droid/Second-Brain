package com.secondbrain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EmbeddingService {

    private static final int EMBEDDING_SIZE = 1536;

    public float[] embed(String text) {
        float[] embedding = new float[EMBEDDING_SIZE];
        int hash = text.hashCode();
        for (int i = 0; i < EMBEDDING_SIZE; i++) {
            embedding[i] = (float) Math.sin(hash * (i + 1) * 0.001);
        }
        return embedding;
    }

    public List<float[]> embedBatch(List<String> texts) {
        return texts.stream()
            .map(this::embed)
            .collect(Collectors.toList());
    }
}
