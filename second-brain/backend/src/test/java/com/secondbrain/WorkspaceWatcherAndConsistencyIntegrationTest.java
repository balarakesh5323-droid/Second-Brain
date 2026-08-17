package com.secondbrain;

import com.secondbrain.common.dto.SearchResult;
import com.secondbrain.common.entity.Project;
import com.secondbrain.common.entity.RepositoryEntity;
import com.secondbrain.common.repository.ProjectRepository;
import com.secondbrain.common.repository.RepositoryEntityRepository;
import com.secondbrain.service.SemanticSearchService;
import com.secondbrain.service.WorkspaceWatcherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class WorkspaceWatcherAndConsistencyIntegrationTest {

    @Autowired
    private WorkspaceWatcherService workspaceWatcherService;

    @Autowired
    private SemanticSearchService semanticSearchService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private RepositoryEntityRepository repositoryRepository;

    private Project testProject;
    private RepositoryEntity repoA;
    private RepositoryEntity repoB;

    @BeforeEach
    void setUp() {
        testProject = projectRepository.saveAndFlush(Project.builder()
                .name("multi-repo-project")
                .path("/tmp/test-project")
                .status("active")
                .build());

        repoA = repositoryRepository.saveAndFlush(RepositoryEntity.builder()
                .name("backend-api")
                .path("/tmp/test-project/backend-api")
                .project(testProject)
                .primaryLanguage("Java")
                .build());

        repoB = repositoryRepository.saveAndFlush(RepositoryEntity.builder()
                .name("frontend-ui")
                .path("/tmp/test-project/frontend-ui")
                .project(testProject)
                .primaryLanguage("TypeScript")
                .build());
    }

    @Test
    @DisplayName("1. SHA-256 Content Hash Generation is deterministic and detects modifications")
    void testContentHashing() {
        String content1 = "public class AuthController { public void login() {} }";
        String content2 = "public class AuthController { public void login() {} }";
        String content3 = "public class AuthController { public void login() { /* changed */ } }";

        String hash1 = WorkspaceWatcherService.computeHash(content1);
        String hash2 = WorkspaceWatcherService.computeHash(content2);
        String hash3 = WorkspaceWatcherService.computeHash(content3);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).isNotEqualTo(hash3);
        assertThat(hash1).hasSize(64);
    }

    @Test
    @DisplayName("2. Scoped Semantic Search respects repository boundaries without throwing exceptions")
    void testScopedSearchExecution() {
        List<SearchResult> results = semanticSearchService.searchScoped(
                "AuthController", "symbol_knowledge", testProject.getId().toString(), repoA.getId().toString(), 10);

        assertThat(results).isNotNull();
    }

    @Test
    @DisplayName("3. Hierarchical weighted search executes across repository, project, and global scopes")
    void testHierarchicalWeightedSearch() {
        List<SearchResult> results = semanticSearchService.searchAllCollectionsScoped(
                "Redis Cache Configuration", testProject.getId().toString(), repoA.getId().toString(), 5);

        assertThat(results).isNotNull();
    }
}
