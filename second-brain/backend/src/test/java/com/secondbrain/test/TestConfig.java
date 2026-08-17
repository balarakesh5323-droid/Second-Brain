package com.secondbrain.test;

import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestConfig {

    @MockBean
    private com.secondbrain.service.VectorStoreService vectorStoreService;

    @MockBean
    private com.secondbrain.service.GraphService graphService;

    @MockBean
    private com.secondbrain.service.EmbeddingService embeddingService;

    @MockBean
    private com.secondbrain.service.DocumentService documentService;

    @MockBean
    private com.secondbrain.service.GitService gitService;

    @MockBean
    private com.secondbrain.service.RepositoryIndexingService repositoryIndexingService;
}
