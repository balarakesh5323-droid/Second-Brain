package com.secondbrain.service;

import com.secondbrain.common.entity.Project;
import com.secondbrain.common.entity.RepositoryEntity;
import com.secondbrain.common.repository.ProjectRepository;
import com.secondbrain.common.repository.RepositoryEntityRepository;
import com.secondbrain.parser.LanguageParserFactory;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceWatcherService {

    private final ProjectRepository projectRepository;
    private final RepositoryEntityRepository repositoryRepository;
    private final GraphService graphService;
    private final LanguageParserFactory languageParserFactory;
    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;
    private final GitService gitService;

    private WatchService watchService;
    private final Map<WatchKey, Path> keyToPath = new ConcurrentHashMap<>();
    private final Map<Path, UUID> pathToProjectId = new ConcurrentHashMap<>();
    private final Map<Path, UUID> pathToRepositoryId = new ConcurrentHashMap<>();
    private final Map<String, Long> debounceMap = new ConcurrentHashMap<>();
    private final Map<String, Long> fileWriteVersions = new ConcurrentHashMap<>();
    private final Map<String, String> indexedContentHashes = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "workspace-file-watcher");
        t.setDaemon(true);
        return t;
    });

    private final ExecutorService workerPool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "workspace-file-worker");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running = false;

    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", "node_modules", "target", "build", "dist", ".gradle",
            "__pycache__", ".idea", ".vscode", "vendor"
    );

    public record RepositoryContext(
            UUID repositoryId,
            UUID projectId,
            String repositoryPath,
            String branch,
            String headCommit,
            String workingTreeState,
            int modifiedFilesCount
    ) {}

    @EventListener(ApplicationReadyEvent.class)
    public void startWatcher() {
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            this.running = true;

            // 1. Warm up persistent content hashes from Neo4j (survives restarts)
            warmupPersistentHashes();

            // 2. Register all existing repository workspace paths (Priority)
            List<RepositoryEntity> repos = repositoryRepository.findAll();
            for (RepositoryEntity repo : repos) {
                if (repo.getPath() != null && !repo.getPath().isBlank()) {
                    watchRepository(repo);
                }
            }

            // 3. Register all existing project workspace paths
            List<Project> projects = projectRepository.findAll();
            for (Project project : projects) {
                if (project.getPath() != null && !project.getPath().isBlank()) {
                    watchProject(project);
                }
            }

            executor.submit(this::watchLoop);
            log.info("Workspace File Watcher initialized and active for {} repos and {} projects", repos.size(), projects.size());
        } catch (Exception e) {
            log.warn("Failed to initialize workspace file watcher: {}", e.getMessage());
        }
    }

    private void warmupPersistentHashes() {
        try {
            Map<String, String> persistedHashes = graphService.findAllFileHashes("");
            indexedContentHashes.putAll(persistedHashes);
            log.info("Warmed up {} persistent content hashes from Neo4j", persistedHashes.size());
        } catch (Exception e) {
            log.debug("Hash cache warmup note: {}", e.getMessage());
        }
    }

    public synchronized void watchRepository(RepositoryEntity repo) {
        if (repo == null || repo.getPath() == null || repo.getPath().isBlank()) return;
        Path root = Paths.get(repo.getPath());
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            log.debug("Workspace path does not exist for repository {}: {}", repo.getName(), repo.getPath());
            return;
        }

        UUID projId = repo.getProject() != null ? repo.getProject().getId() : null;
        registerRecursive(root, projId, repo.getId(), "repository '" + repo.getName() + "'");
    }

    public synchronized void watchProject(Project project) {
        if (project == null || project.getPath() == null || project.getPath().isBlank()) return;
        Path root = Paths.get(project.getPath());
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            log.debug("Workspace path does not exist for project {}: {}", project.getName(), project.getPath());
            return;
        }

        registerRecursive(root, project.getId(), null, "project '" + project.getName() + "'");
    }

    private void registerRecursive(Path root, UUID projectId, UUID repoId, String label) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (IGNORED_DIRS.contains(dirName) || dirName.startsWith(".")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (watchService != null) {
                        WatchKey key = dir.register(watchService,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_MODIFY,
                                StandardWatchEventKinds.ENTRY_DELETE);
                        keyToPath.put(key, dir);
                        if (projectId != null) pathToProjectId.put(dir, projectId);
                        if (repoId != null) pathToRepositoryId.put(dir, repoId);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            log.info("Watching workspace for {} at: {}", label, root);
        } catch (Exception e) {
            log.warn("Error registering watcher for {}: {}", root, e.getMessage());
        }
    }

    private void watchLoop() {
        while (running && watchService != null) {
            WatchKey key;
            try {
                key = watchService.poll(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            if (key == null) continue;

            Path dir = keyToPath.get(key);
            if (dir == null) {
                key.cancel();
                continue;
            }

            UUID projectId = pathToProjectId.get(dir);
            UUID repoId = pathToRepositoryId.get(dir);

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    log.warn("Watcher OVERFLOW event in directory '{}', scheduling full reconciliation", dir);
                    if (repoId != null) {
                        workerPool.submit(() -> reconcileRepository(repoId));
                    } else if (projectId != null) {
                        workerPool.submit(() -> reconcileProject(projectId));
                    }
                    continue;
                }

                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path filename = ev.context();
                Path fullPath = dir.resolve(filename);

                // If a new directory was created, watch it recursively
                if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(fullPath)) {
                    String dirName = fullPath.getFileName().toString();
                    if (!IGNORED_DIRS.contains(dirName) && !dirName.startsWith(".")) {
                        registerRecursive(fullPath, projectId, repoId, "subdirectory '" + dirName + "'");
                    }
                    continue;
                }

                // Debounce rapid writes (300ms window)
                String pathStr = fullPath.toString();
                long now = System.currentTimeMillis();
                Long last = debounceMap.get(pathStr);
                if (last != null && (now - last) < 300) {
                    continue;
                }
                debounceMap.put(pathStr, now);

                // Monotonically increasing version counter for race-safe worker execution
                long eventVersion = System.nanoTime();
                fileWriteVersions.put(pathStr, eventVersion);

                if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                    workerPool.submit(() -> handleFileChange(fullPath, projectId, repoId, eventVersion, null));
                } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                    workerPool.submit(() -> handleFileDelete(fullPath, projectId, repoId, eventVersion));
                }
            }

            boolean valid = key.reset();
            if (!valid) {
                keyToPath.remove(key);
                pathToProjectId.remove(dir);
                pathToRepositoryId.remove(dir);
            }
        }
    }

    public RepositoryContext resolveRepositoryContext(UUID repoId, UUID projectId, Path root) {
        String repoPath = root.toString();
        String branch = "main";
        String headCommit = "uncommitted";
        String workingTreeState = "CLEAN";
        int modifiedCount = 0;

        try {
            branch = gitService.getCurrentBranch(repoPath);
            List<Map<String, Object>> commits = gitService.getRecentCommits(repoPath, 1);
            if (!commits.isEmpty()) {
                headCommit = (String) commits.get(0).getOrDefault("hash", "uncommitted");
            }

            Map<String, Object> status = gitService.getWorkingTreeStatus(repoPath);
            workingTreeState = (String) status.getOrDefault("state", "CLEAN");
            modifiedCount = (Integer) status.getOrDefault("modifiedCount", 0);
        } catch (Exception ignored) {}

        return new RepositoryContext(repoId, projectId, repoPath, branch, headCommit, workingTreeState, modifiedCount);
    }

    public void reconcileRepository(UUID repoId) {
        if (repoId == null) return;
        var rOpt = repositoryRepository.findById(repoId);
        if (rOpt.isEmpty() || rOpt.get().getPath() == null) return;
        Path root = Paths.get(rOpt.get().getPath());
        if (!Files.exists(root)) return;

        UUID projId = rOpt.get().getProject() != null ? rOpt.get().getProject().getId() : null;
        RepositoryContext ctx = resolveRepositoryContext(repoId, projId, root);
        reconcilePath(root, projId, repoId, "repo::" + repoId.toString(), ctx);
    }

    public void reconcileProject(UUID projectId) {
        if (projectId == null) return;
        var pOpt = projectRepository.findById(projectId);
        if (pOpt.isEmpty() || pOpt.get().getPath() == null) return;
        Path root = Paths.get(pOpt.get().getPath());
        if (!Files.exists(root)) return;

        RepositoryContext ctx = resolveRepositoryContext(null, projectId, root);
        reconcilePath(root, projectId, null, "proj::" + projectId.toString(), ctx);
    }

    private void reconcilePath(Path root, UUID projectId, UUID repoId, String prefix, RepositoryContext ctx) {
        log.info("Starting complete incremental reconciliation for prefix '{}' at {}", prefix, root);
        Set<Path> diskFiles = new HashSet<>();

        // Warm up persistent content hashes for this scope
        indexedContentHashes.putAll(graphService.findAllFileHashes(prefix));

        // 1. Walk entire disk tree unconstrained (filtering ignored dirs)
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (IGNORED_DIRS.contains(dirName) || dirName.startsWith(".")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        diskFiles.add(file.toAbsolutePath().normalize());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            log.warn("Error walking directory {}: {}", root, e.getMessage());
        }

        // 2. Discover deleted files: Compare indexed files in graph vs current disk files
        List<Map<String, String>> indexedFiles = graphService.findFilesByPrefix(prefix);
        for (Map<String, String> indexed : indexedFiles) {
            String pStr = indexed.get("path");
            if (pStr != null && !pStr.isBlank()) {
                Path p = Paths.get(pStr).toAbsolutePath().normalize();
                if (!diskFiles.contains(p) && !Files.exists(p)) {
                    log.info("Reconciliation: Detected deleted file '{}', removing from brain", indexed.get("id"));
                    long deleteVersion = System.nanoTime();
                    fileWriteVersions.put(p.toString(), deleteVersion);
                    handleFileDelete(p, projectId, repoId, deleteVersion);
                }
            }
        }

        // 3. Process existing files incrementally (using cached Git context)
        for (Path f : diskFiles) {
            long ver = System.nanoTime();
            fileWriteVersions.put(f.toString(), ver);
            handleFileChange(f, projectId, repoId, ver, ctx);
        }
    }

    private String computeFileId(Path filePath, UUID projectId, UUID repoId) {
        String relPath = filePath.getFileName().toString();
        if (repoId != null) {
            var r = repositoryRepository.findById(repoId);
            if (r.isPresent() && r.get().getPath() != null) {
                try {
                    relPath = Paths.get(r.get().getPath()).relativize(filePath).toString();
                } catch (Exception ignored) {}
            }
            return "repo::" + repoId + "::" + relPath;
        } else if (projectId != null) {
            var p = projectRepository.findById(projectId);
            if (p.isPresent() && p.get().getPath() != null) {
                try {
                    relPath = Paths.get(p.get().getPath()).relativize(filePath).toString();
                } catch (Exception ignored) {}
            }
            return "proj::" + projectId + "::" + relPath;
        }
        return "global::" + relPath;
    }

    public static String computeHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(content.hashCode());
        }
    }

    private void handleFileChange(Path filePath, UUID projectId, UUID repoId, long eventVersion, RepositoryContext cachedCtx) {
        String pathStr = filePath.toString();

        // 1. Pre-execution version validation
        Long currentVer = fileWriteVersions.get(pathStr);
        if (currentVer != null && currentVer > eventVersion) {
            log.debug("Skipping stale queued write for file: {} (queued: {}, current: {})", pathStr, eventVersion, currentVer);
            return;
        }

        if (Files.isDirectory(filePath) || !Files.exists(filePath)) return;
        String fileName = filePath.getFileName().toString();
        if (fileName.startsWith(".") || fileName.endsWith("~") || fileName.endsWith(".tmp")) return;

        try {
            String content = Files.readString(filePath);
            String contentHash = computeHash(content);

            String projIdStr = projectId != null ? projectId.toString() : "";
            String repoIdStr = repoId != null ? repoId.toString() : "";
            String fileId = computeFileId(filePath, projectId, repoId);

            // 2. Incremental Content-Hash Check: Skip unchanged files
            String previousHash = indexedContentHashes.get(fileId);
            if (contentHash.equals(previousHash)) {
                log.debug("Incremental Indexing: File '{}' content unchanged (hash: {}), skipping re-indexing", fileId, contentHash);
                return;
            }

            Map<String, Object> structure = languageParserFactory.parseFile(filePath.toString(), content);
            if (structure == null) return;

            // Resolve Git metadata (using cached context if available)
            String branch = cachedCtx != null ? cachedCtx.branch() : "main";
            String commitSha = cachedCtx != null ? cachedCtx.headCommit() : "uncommitted";
            if (cachedCtx == null) {
                try {
                    String repoDir = repoId != null && repositoryRepository.findById(repoId).isPresent()
                            ? repositoryRepository.findById(repoId).get().getPath()
                            : (projectId != null && projectRepository.findById(projectId).isPresent()
                                ? projectRepository.findById(projectId).get().getPath()
                                : null);
                    if (repoDir != null) {
                        branch = gitService.getCurrentBranch(repoDir);
                        List<Map<String, Object>> commits = gitService.getRecentCommits(repoDir, 1);
                        if (!commits.isEmpty()) {
                            commitSha = (String) commits.get(0).getOrDefault("hash", "uncommitted");
                        }
                    }
                } catch (Exception ignored) {}
            }

            // 3. Pre-Commit Monotonic Race Check: Verify no newer write or delete arrived during parsing/embedding
            Long latestVer = fileWriteVersions.get(pathStr);
            if (latestVer != null && latestVer > eventVersion) {
                log.info("Discarding completed parse for '{}' as newer event superseded it (event: {}, latest: {})", fileId, eventVersion, latestVer);
                return;
            }

            log.info("⚡ Instant Auto-Sync: Committing file '{}' (hash: {}, branch: {}, commit: {}) ({} functions)",
                    fileId, contentHash.substring(0, 8), branch, commitSha,
                    ((List<?>) structure.getOrDefault("functions", List.of())).size());

            // A. Update File node in Neo4j
            graphService.createNode("File", fileId, Map.of(
                    "name", fileName,
                    "path", filePath.toString(),
                    "language", structure.getOrDefault("language", "unknown"),
                    "branch", branch,
                    "commitSha", commitSha,
                    "contentHash", contentHash
            ));

            if (repoId != null) {
                graphService.createRelationship("Repository", repoIdStr, "File", fileId, "CONTAINS", null);
            } else if (projectId != null) {
                graphService.createRelationship("Project", projIdStr, "File", fileId, "CONTAINS", null);
            }

            // B. Reconcile Functions & Classes (Stale Child Removal)
            Set<String> currentChildIds = new HashSet<>();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> functions = (List<Map<String, Object>>) structure.getOrDefault("functions", List.of());
            for (Map<String, Object> func : functions) {
                String funcName = (String) func.getOrDefault("name", "unknown");
                String funcId = fileId + "::" + funcName;
                currentChildIds.add(funcId);

                Map<String, Object> props = new HashMap<>();
                props.put("name", funcName);
                props.put("file", fileName);
                props.put("returnType", func.getOrDefault("returnType", "void"));
                props.put("parameters", String.valueOf(func.getOrDefault("parameters", "")));
                props.put("branch", branch);
                props.put("commitSha", commitSha);
                graphService.createNode("Function", funcId, props);
                graphService.createRelationship("File", fileId, "Function", funcId, "DECLARES", null);

                // Embed symbol into vector store
                try {
                    String symbolDoc = String.format("Function %s(%s) -> %s in %s (branch: %s)",
                            funcName, props.get("parameters"), props.get("returnType"), fileName, branch);
                    float[] vec = embeddingService.embed(symbolDoc);
                    if (vec != null) {
                        String pointId = UUID.nameUUIDFromBytes(funcId.getBytes()).toString();
                        Map<String, String> payload = new HashMap<>();
                        payload.put("name", funcName);
                        payload.put("file", fileName);
                        payload.put("fileId", fileId);
                        if (!projIdStr.isBlank()) payload.put("projectId", projIdStr);
                        if (!repoIdStr.isBlank()) payload.put("repositoryId", repoIdStr);
                        payload.put("branch", branch);
                        payload.put("commitSha", commitSha);
                        payload.put("type", "function");
                        payload.put("doc", symbolDoc);
                        vectorStoreService.upsert("symbol_knowledge", pointId, vec, payload, Map.of());
                    }
                } catch (Exception ignored) {}
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> classes = (List<Map<String, Object>>) structure.getOrDefault("classes", List.of());
            for (Map<String, Object> cls : classes) {
                String clsName = (String) cls.getOrDefault("name", "unknown");
                String clsId = fileId + "::" + clsName;
                currentChildIds.add(clsId);

                graphService.createNode("Class", clsId, Map.of(
                        "name", clsName,
                        "file", fileName,
                        "branch", branch,
                        "type", cls.getOrDefault("type", "class")
                ));
                graphService.createRelationship("File", fileId, "Class", clsId, "DECLARES", null);
            }

            // Atomic cleanup of stale children in Neo4j and Qdrant
            List<String> deletedChildIds = graphService.deleteStaleChildren(fileId, currentChildIds);
            for (String deletedId : deletedChildIds) {
                String pointId = UUID.nameUUIDFromBytes(deletedId.getBytes()).toString();
                vectorStoreService.delete("symbol_knowledge", pointId);
                log.info("⚡ Purged stale symbol vector '{}' from Qdrant", deletedId);
            }

            // C. Reconcile Architectural Technologies (Delete obsolete tech links)
            extractAndReconcileArchitecturalPatterns(content, fileName, fileId);

            // Record successful indexing hash
            indexedContentHashes.put(fileId, contentHash);

        } catch (Exception e) {
            log.debug("Auto-sync error on {}: {}", filePath, e.getMessage());
        }
    }

    private void extractAndReconcileArchitecturalPatterns(String content, String fileName, String fileId) {
        try {
            Map<String, String> detectedTech = new HashMap<>();

            if (content.contains("RedisTemplate") || content.contains("StringRedisTemplate")) {
                detectedTech.put("Redis", "In-Memory Key-Value Data Store");
            }
            if (content.contains("@Cacheable") || content.contains("@CachePut") || content.contains("@CacheEvict")) {
                detectedTech.put("Spring Cache", "Caching Abstraction Layer");
            }
            if (content.contains("@KafkaListener") || content.contains("KafkaTemplate")) {
                detectedTech.put("Kafka", "Event-Driven Messaging Stream");
            }
            if (content.contains("@RabbitListener") || content.contains("RabbitTemplate")) {
                detectedTech.put("RabbitMQ", "AMQP Message Broker");
            }
            if (content.contains("@Transactional")) {
                detectedTech.put("Spring Transaction", "Declarative ACID Transactions");
            }
            if (content.contains("@RestController") || content.contains("@GetMapping") || content.contains("@PostMapping")) {
                detectedTech.put("Spring Web", "REST API Endpoint Gateway");
            }
            if (content.contains("useState") || content.contains("useEffect") || content.contains("import React")) {
                detectedTech.put("React.js", "Frontend Component Architecture");
            }
            if (content.contains("getContext('2d')") || content.contains("requestAnimationFrame")) {
                detectedTech.put("HTML5 Canvas", "2D Interactive Graphics Engine");
            }

            Set<String> keepTechIds = new HashSet<>();
            for (Map.Entry<String, String> entry : detectedTech.entrySet()) {
                String techName = entry.getKey();
                String patternDesc = entry.getValue();
                String techId = "tech::" + techName.toLowerCase().replaceAll("[^a-z0-9]", "_");
                keepTechIds.add(techId);

                graphService.createNode("Technology", techId, Map.of(
                        "name", techName,
                        "pattern", patternDesc
                ));
                graphService.createRelationship("File", fileId, "Technology", techId, "USES_TECHNOLOGY", Map.of("pattern", patternDesc));
            }

            // Remove obsolete technology relationships if removed from source file
            graphService.deleteStaleTechnologies(fileId, keepTechIds);

        } catch (Exception e) {
            log.debug("Pattern extraction error on {}: {}", fileName, e.getMessage());
        }
    }

    private void handleFileDelete(Path filePath, UUID projectId, UUID repoId, long deleteVersion) {
        String pathStr = filePath.toString();

        // Check if a newer modify event arrived after this delete was requested
        Long latestVer = fileWriteVersions.get(pathStr);
        if (latestVer != null && latestVer > deleteVersion) {
            log.info("Discarding delete for '{}' as newer write event arrived (del: {}, latest: {})", pathStr, deleteVersion, latestVer);
            return;
        }

        String fileId = computeFileId(filePath, projectId, repoId);

        try {
            // 1. Delete from Neo4j (File and all declared child Function/Class nodes)
            graphService.deleteFileCascade(fileId);

            // 2. Delete vectors from Qdrant
            vectorStoreService.deleteByFile("symbol_knowledge", fileId);
            vectorStoreService.deleteByFile("code_knowledge", fileId);

            // 3. Clear indexing cache
            indexedContentHashes.remove(fileId);

            log.info("⚡ Instant Auto-Sync: Cascaded delete of file node '{}' and vectors from Qdrant", fileId);
        } catch (Exception e) {
            log.debug("Error deleting node {}: {}", fileId, e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        this.running = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {}
        }
        workerPool.shutdownNow();
        executor.shutdownNow();
    }
}
