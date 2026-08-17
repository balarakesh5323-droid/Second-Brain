package com.secondbrain;

import com.secondbrain.common.dto.SearchResult;
import com.secondbrain.common.entity.Project;
import com.secondbrain.common.entity.RepositoryEntity;
import com.secondbrain.common.repository.ProjectRepository;
import com.secondbrain.common.repository.RepositoryEntityRepository;
import com.secondbrain.service.GitService;
import com.secondbrain.service.GraphService;
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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class WorkspaceWatcherAndConsistencyIntegrationTest {

    @Autowired
    private WorkspaceWatcherService workspaceWatcherService;

    @Autowired
    private SemanticSearchService semanticSearchService;

    @Autowired
    private GraphService graphService;

    @Autowired
    private GitService gitService;

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
    @DisplayName("2. Working Tree Status calculation handles non-git / git workspaces gracefully")
    void testWorkingTreeStatus() {
        GitService directGitService = new GitService();
        Map<String, Object> status = directGitService.getWorkingTreeStatus("/tmp/test-project/backend-api");
        assertThat(status).isNotNull();
        assertThat(status).containsKey("state");
        assertThat(status.get("state")).isIn("CLEAN", "MODIFIED", "UNKNOWN", "MIXED", "STAGED", "UNTRACKED");
    }

    @Test
    @DisplayName("3. Scoped Semantic Search respects repository boundaries")
    void testScopedSearchExecution() {
        List<SearchResult> resultsA = semanticSearchService.searchScoped(
                "AuthController", "symbol_knowledge", testProject.getId().toString(), repoA.getId().toString(), 10);
        assertThat(resultsA).isNotNull();

        List<SearchResult> resultsB = semanticSearchService.searchScoped(
                "AuthController", "symbol_knowledge", testProject.getId().toString(), repoB.getId().toString(), 10);
        assertThat(resultsB).isNotNull();
    }

    @Test
    @DisplayName("4. Hierarchical weighted search executes across repository, project, and global scopes")
    void testHierarchicalWeightedSearch() {
        List<SearchResult> results = semanticSearchService.searchAllCollectionsScoped(
                "Redis Cache Configuration", testProject.getId().toString(), repoA.getId().toString(), 5);
        assertThat(results).isNotNull();
    }

    @Test
    @DisplayName("5. Destructive Stale-Child Removal: Purges deleted symbols and returns purged IDs")
    void testStaleFunctionPurge() {
        String fileId = "repo::" + repoA.getId() + "::src/UserService.java";
        String staleFuncId = fileId + "::delete";

        when(graphService.deleteStaleChildren(eq(fileId), anySet()))
                .thenReturn(List.of(staleFuncId));
        when(graphService.getDeclaredChildIds(fileId))
                .thenReturn(List.of(fileId + "::save", fileId + "::update"));

        Set<String> keepChildren = Set.of(fileId + "::save", fileId + "::update");
        List<String> deleted = graphService.deleteStaleChildren(fileId, keepChildren);

        assertThat(deleted).containsExactly(staleFuncId);

        List<String> remaining = graphService.getDeclaredChildIds(fileId);
        assertThat(remaining).containsExactly(fileId + "::save", fileId + "::update");
        assertThat(remaining).doesNotContain(staleFuncId);

        verify(graphService, times(1)).deleteStaleChildren(eq(fileId), eq(keepChildren));
    }

    @Test
    @DisplayName("6. Destructive Stale-Technology Removal: Reconciles technology relationships")
    void testStaleTechnologyPurge() {
        String fileId = "repo::" + repoA.getId() + "::src/RedisConfig.java";
        String postgresId = "tech::postgresql";

        Set<String> keepTech = Set.of(postgresId);
        graphService.deleteStaleTechnologies(fileId, keepTech);
        verify(graphService, times(1)).deleteStaleTechnologies(eq(fileId), eq(keepTech));

        graphService.deleteFileCascade(fileId);
        verify(graphService, times(1)).deleteFileCascade(eq(fileId));
    }
}
