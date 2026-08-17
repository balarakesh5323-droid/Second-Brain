package com.secondbrain.service;

import com.secondbrain.common.entity.Project;
import com.secondbrain.common.repository.ProjectRepository;
import com.secondbrain.parser.LanguageParserFactory;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceWatcherService {

    private final ProjectRepository projectRepository;
    private final GraphService graphService;
    private final LanguageParserFactory languageParserFactory;
    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;

    private WatchService watchService;
    private final Map<WatchKey, Path> keyToPath = new ConcurrentHashMap<>();
    private final Map<Path, UUID> pathToProjectId = new ConcurrentHashMap<>();
    private final Map<String, Long> debounceMap = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "workspace-file-watcher");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running = false;

    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", "node_modules", "target", "build", "dist", ".gradle",
            "__pycache__", ".idea", ".vscode", "vendor"
    );

    @EventListener(ApplicationReadyEvent.class)
    public void startWatcher() {
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            this.running = true;

            // Register all existing project workspace paths
            List<Project> projects = projectRepository.findAll();
            for (Project project : projects) {
                if (project.getPath() != null && !project.getPath().isBlank()) {
                    watchProject(project);
                }
            }

            executor.submit(this::watchLoop);
            log.info("Workspace File Watcher initialized and active for {} projects", projects.size());
        } catch (Exception e) {
            log.warn("Failed to initialize workspace file watcher: {}", e.getMessage());
        }
    }

    public synchronized void watchProject(Project project) {
        if (project == null || project.getPath() == null || project.getPath().isBlank()) return;
        Path root = Paths.get(project.getPath());
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            log.debug("Workspace path does not exist for project {}: {}", project.getName(), project.getPath());
            return;
        }

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
                        pathToProjectId.put(dir, project.getId());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            log.info("Watching workspace for project '{}' at: {}", project.getName(), root);
        } catch (Exception e) {
            log.warn("Error registering watcher for {}: {}", project.getPath(), e.getMessage());
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

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path filename = ev.context();
                Path fullPath = dir.resolve(filename);

                // If a new directory was created, watch it
                if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(fullPath)) {
                    String dirName = fullPath.getFileName().toString();
                    if (!IGNORED_DIRS.contains(dirName) && !dirName.startsWith(".")) {
                        try {
                            WatchKey newKey = fullPath.register(watchService,
                                    StandardWatchEventKinds.ENTRY_CREATE,
                                    StandardWatchEventKinds.ENTRY_MODIFY,
                                    StandardWatchEventKinds.ENTRY_DELETE);
                            keyToPath.put(newKey, fullPath);
                            if (projectId != null) {
                                pathToProjectId.put(fullPath, projectId);
                            }
                        } catch (IOException ignored) {}
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

                if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                    handleFileChange(fullPath, projectId);
                } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                    handleFileDelete(fullPath, projectId);
                }
            }

            boolean valid = key.reset();
            if (!valid) {
                keyToPath.remove(key);
                pathToProjectId.remove(dir);
            }
        }
    }

    private void handleFileChange(Path filePath, UUID projectId) {
        if (Files.isDirectory(filePath) || !Files.exists(filePath)) return;
        String fileName = filePath.getFileName().toString();
        if (fileName.startsWith(".") || fileName.endsWith("~") || fileName.endsWith(".tmp")) return;

        try {
            String content = Files.readString(filePath);
            Map<String, Object> structure = languageParserFactory.parseFile(filePath.toString(), content);
            if (structure == null) return;

            String projIdStr = projectId != null ? projectId.toString() : "global";
            String fileId = projIdStr + "::" + fileName;

            log.info("⚡ Instant Auto-Sync: Parsed file '{}' ({} functions) in workspace", fileName,
                    ((List<?>) structure.getOrDefault("functions", List.of())).size());

            // 1. Update File node in Neo4j
            graphService.createNode("File", fileId, Map.of(
                    "name", fileName,
                    "path", filePath.toString(),
                    "language", structure.getOrDefault("language", "unknown")
            ));

            if (projectId != null) {
                graphService.createRelationship("Project", projIdStr, "File", fileId, "CONTAINS", null);
            }

            // 2. Update Functions in Neo4j
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> functions = (List<Map<String, Object>>) structure.getOrDefault("functions", List.of());
            for (Map<String, Object> func : functions) {
                String funcName = (String) func.getOrDefault("name", "unknown");
                String funcId = fileId + "::" + funcName;
                Map<String, Object> props = new HashMap<>();
                props.put("name", funcName);
                props.put("file", fileName);
                props.put("returnType", func.getOrDefault("returnType", "void"));
                props.put("parameters", String.valueOf(func.getOrDefault("parameters", "")));
                graphService.createNode("Function", funcId, props);
                graphService.createRelationship("File", fileId, "Function", funcId, "DECLARES", null);

                // Embed symbol into vector store
                try {
                    String symbolDoc = String.format("Function %s(%s) -> %s in %s",
                            funcName, props.get("parameters"), props.get("returnType"), fileName);
                    float[] vec = embeddingService.embed(symbolDoc);
                    if (vec != null) {
                        String pointId = UUID.nameUUIDFromBytes(funcId.getBytes()).toString();
                        vectorStoreService.upsert("symbol_knowledge", pointId, vec, Map.of(
                                "name", funcName,
                                "file", fileName,
                                "projectId", projIdStr,
                                "type", "function",
                                "doc", symbolDoc
                        ), Map.of());
                    }
                } catch (Exception ignored) {}
            }

            // 3. Update Classes in Neo4j
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> classes = (List<Map<String, Object>>) structure.getOrDefault("classes", List.of());
            for (Map<String, Object> cls : classes) {
                String clsName = (String) cls.getOrDefault("name", "unknown");
                String clsId = fileId + "::" + clsName;
                graphService.createNode("Class", clsId, Map.of(
                        "name", clsName,
                        "file", fileName,
                        "type", cls.getOrDefault("type", "class")
                ));
                graphService.createRelationship("File", fileId, "Class", clsId, "DECLARES", null);
            }

            // 4. Architectural Event-to-Knowledge Discovery Pipeline
            extractArchitecturalPatterns(content, fileName, fileId, projIdStr);

        } catch (Exception e) {
            log.debug("Auto-sync error on {}: {}", filePath, e.getMessage());
        }
    }

    private void extractArchitecturalPatterns(String content, String fileName, String fileId, String projIdStr) {
        try {
            Map<String, String> detectedTech = new HashMap<>();

            if (content.contains("@Cacheable") || content.contains("@CachePut") || content.contains("@CacheEvict") || content.contains("RedisTemplate")) {
                detectedTech.put("Redis", "Caching Layer");
            }
            if (content.contains("@KafkaListener") || content.contains("KafkaTemplate")) {
                detectedTech.put("Kafka", "Event-Driven Messaging");
            }
            if (content.contains("@RabbitListener") || content.contains("RabbitTemplate")) {
                detectedTech.put("RabbitMQ", "Message Broker");
            }
            if (content.contains("@Transactional")) {
                detectedTech.put("PostgreSQL", "ACID Transactional Persistence");
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

            for (Map.Entry<String, String> entry : detectedTech.entrySet()) {
                String techName = entry.getKey();
                String patternDesc = entry.getValue();
                String techId = "tech::" + techName.toLowerCase().replaceAll("[^a-z0-9]", "_");

                graphService.createNode("Technology", techId, Map.of(
                        "name", techName,
                        "pattern", patternDesc
                ));
                graphService.createRelationship("File", fileId, "Technology", techId, "USES_TECHNOLOGY", Map.of("pattern", patternDesc));
                log.info("🧠 Event-to-Knowledge Pipeline: Linked file '{}' -> Technology '{}' ({})", fileName, techName, patternDesc);
            }
        } catch (Exception e) {
            log.debug("Pattern extraction error on {}: {}", fileName, e.getMessage());
        }
    }

    private void handleFileDelete(Path filePath, UUID projectId) {
        String projIdStr = projectId != null ? projectId.toString() : "global";
        String fileName = filePath.getFileName().toString();
        String fileId = projIdStr + "::" + fileName;

        try {
            graphService.deleteNode(fileId);
            log.info("⚡ Instant Auto-Sync: Removed deleted file node '{}' from graph", fileId);
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
        executor.shutdownNow();
    }
}
