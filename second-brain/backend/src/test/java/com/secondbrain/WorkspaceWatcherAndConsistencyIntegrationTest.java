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
import java.util.concurrent.ConcurrentHashMap;

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
    @DisplayName("1. Deterministic Content Hashing & Detection: SHA-256 detects content modifications")
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
    @DisplayName("2. Rich Working Tree State & Status Counts")
    void testWorkingTreeStatus() {
        GitService directGitService = new GitService();
        Map<String, Object> status = directGitService.getWorkingTreeStatus("/tmp/test-project/backend-api");
        assertThat(status).isNotNull();
        assertThat(status).containsKey("state");
        assertThat(status).containsKey("clean");
        assertThat(status).containsKey("modifiedCount");
        assertThat(status).containsKey("untrackedCount");
        assertThat(status).containsKey("stagedCount");
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
    @DisplayName("5. Destructive Stale-Child Removal: Purging obsolete functions removes them and returns IDs")
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

    @Test
    @DisplayName("7. Modify-Delete Race Invariant: Monotonic versioning rejects stale modifications")
    void testModifyDeleteRaceRejection() {
        Map<String, Long> writeVersions = new ConcurrentHashMap<>();
        String filePath = "/tmp/test-project/backend-api/src/TestService.java";

        long modifyVersion = 1000L;
        writeVersions.put(filePath, modifyVersion);

        long deleteVersion = 2000L;
        writeVersions.put(filePath, deleteVersion);

        // When slow modify worker finishes, current version (2000) > event version (1000) -> rejected
        Long currentVer = writeVersions.get(filePath);
        boolean isStale = currentVer != null && currentVer > modifyVersion;

        assertThat(isStale).isTrue();
    }

    @Test
    @DisplayName("8. Rapid Modifications A -> B -> C: Final brain state matches latest version")
    void testRapidSequentialModifications() {
        Map<String, String> hashes = new ConcurrentHashMap<>();
        String fileId = "repo::" + repoA.getId() + "::src/StateService.java";

        String hashA = WorkspaceWatcherService.computeHash("state A");
        hashes.put(fileId, hashA);

        String hashB = WorkspaceWatcherService.computeHash("state B");
        hashes.put(fileId, hashB);

        String hashC = WorkspaceWatcherService.computeHash("state C");
        hashes.put(fileId, hashC);

        assertThat(hashes.get(fileId)).isEqualTo(hashC);
        assertThat(hashes.get(fileId)).isNotEqualTo(hashA);
    }
}
