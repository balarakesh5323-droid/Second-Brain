package com.secondbrain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepositoryBootstrapService {

    private final GraphService graphService;

    public BootstrapResult bootstrap(String repoPath) {
        log.info("Bootstrapping repository: {}", repoPath);
        Path root = Paths.get(repoPath);

        if (!Files.exists(root)) {
            return BootstrapResult.builder()
                .status("error")
                .error("Path does not exist: " + repoPath)
                .build();
        }

        BootstrapResult result = BootstrapResult.builder()
            .path(repoPath)
            .detectedAt(java.time.LocalDateTime.now())
            .build();

        // Detect Git
        result.git = detectGit(root);

        // Detect language
        result.languages = detectLanguages(root);

        // Detect frameworks
        result.frameworks = detectFrameworks(root);

        // Detect databases
        result.databases = detectDatabases(root);

        // Detect Docker
        result.docker = detectDocker(root);

        // Detect Kubernetes
        result.kubernetes = detectKubernetes(root);

        // Detect package managers
        result.packageManagers = detectPackageManagers(root);

        // Detect CI/CD
        result.cicd = detectCICD(root);

        // Create knowledge graph nodes
        createGraphNodes(result);

        result.status = "bootstrapped";
        log.info("Bootstrap complete for {}: {} languages, {} frameworks, {} databases",
            repoPath, result.languages.size(), result.frameworks.size(), result.databases.size());

        return result;
    }

    private GitInfo detectGit(Path root) {
        Path gitDir = root.resolve(".git");
        boolean isGit = Files.exists(gitDir);
        String defaultBranch = "main";

        if (isGit) {
            try {
                Path head = gitDir.resolve("HEAD");
                if (Files.exists(head)) {
                    String content = Files.readString(head).trim();
                    if (content.startsWith("ref: refs/heads/")) {
                        defaultBranch = content.substring("ref: refs/heads/".length());
                    }
                }
            } catch (IOException e) {
                log.debug("Could not read HEAD: {}", e.getMessage());
            }
        }

        return GitInfo.builder()
            .isGitRepository(isGit)
            .defaultBranch(defaultBranch)
            .build();
    }

    private List<String> detectLanguages(Path root) {
        Set<String> languages = new LinkedHashSet<>();
        Map<String, Integer> extCounts = new HashMap<>();

        try (Stream<Path> files = Files.walk(root, 8)) {
            files.filter(Files::isRegularFile)
                .filter(p -> !p.toString().contains(".git"))
                .filter(p -> !p.toString().contains("node_modules"))
                .filter(p -> !p.toString().contains("target"))
                .filter(p -> !p.toString().contains("build"))
                .forEach(p -> {
                    String ext = getExtension(p.getFileName().toString());
                    if (!ext.isEmpty()) {
                        extCounts.merge(ext, 1, Integer::sum);
                    }
                });
        } catch (IOException e) {
            log.debug("Language detection failed: {}", e.getMessage());
        }

        // Map extensions to languages
        Map<String, String> extToLang = Map.ofEntries(
            Map.entry("java", "Java"),
            Map.entry("py", "Python"),
            Map.entry("js", "JavaScript"),
            Map.entry("ts", "TypeScript"),
            Map.entry("tsx", "TypeScript"),
            Map.entry("jsx", "JavaScript"),
            Map.entry("go", "Go"),
            Map.entry("rs", "Rust"),
            Map.entry("rb", "Ruby"),
            Map.entry("php", "PHP"),
            Map.entry("cs", "C#"),
            Map.entry("cpp", "C++"),
            Map.entry("c", "C"),
            Map.entry("kt", "Kotlin"),
            Map.entry("scala", "Scala"),
            Map.entry("sql", "SQL"),
            Map.entry("sh", "Shell"),
            Map.entry("yaml", "YAML"),
            Map.entry("yml", "YAML"),
            Map.entry("json", "JSON"),
            Map.entry("xml", "XML"),
            Map.entry("html", "HTML"),
            Map.entry("css", "CSS"),
            Map.entry("md", "Markdown"),
            Map.entry("tf", "Terraform"),
            Map.entry("hcl", "Terraform")
        );

        extCounts.forEach((ext, count) -> {
            String lang = extToLang.getOrDefault(ext, ext.toUpperCase());
            if (count >= 1) languages.add(lang);
        });

        return new ArrayList<>(languages);
    }

    private List<String> detectFrameworks(Path root) {
        List<String> frameworks = new ArrayList<>();

        // Java frameworks
        if (Files.exists(root.resolve("pom.xml"))) {
            frameworks.add("Maven");
            if (Files.exists(root.resolve("src/main/java"))) {
                frameworks.add("Java");
            }
        }
        if (Files.exists(root.resolve("build.gradle")) || Files.exists(root.resolve("build.gradle.kts"))) {
            frameworks.add("Gradle");
        }

        // Spring Boot detection
        if (hasFileContaining(root, "build.gradle", "spring-boot")) frameworks.add("Spring Boot");
        if (hasFileContaining(root, "pom.xml", "spring-boot")) frameworks.add("Spring Boot");

        // JavaScript/Node
        if (Files.exists(root.resolve("package.json"))) {
            frameworks.add("Node.js");
            String pkg = readSafe(root.resolve("package.json"));
            if (pkg.contains("react")) frameworks.add("React");
            if (pkg.contains("vue")) frameworks.add("Vue.js");
            if (pkg.contains("angular")) frameworks.add("Angular");
            if (pkg.contains("next")) frameworks.add("Next.js");
            if (pkg.contains("express")) frameworks.add("Express.js");
            if (pkg.contains("spring-boot") || pkg.contains("vite")) frameworks.add("Vite");
            if (pkg.contains("tailwind")) frameworks.add("Tailwind CSS");
        }

        // Python
        if (Files.exists(root.resolve("requirements.txt")) || Files.exists(root.resolve("pyproject.toml"))) {
            frameworks.add("Python");
            String req = readSafe(root.resolve("requirements.txt"));
            String pyproject = readSafe(root.resolve("pyproject.toml"));
            String allPython = req + pyproject;
            if (allPython.contains("django")) frameworks.add("Django");
            if (allPython.contains("flask")) frameworks.add("Flask");
            if (allPython.contains("fastapi")) frameworks.add("FastAPI");
        }

        // Go
        if (Files.exists(root.resolve("go.mod"))) frameworks.add("Go");

        // Rust
        if (Files.exists(root.resolve("Cargo.toml"))) frameworks.add("Rust");

        // .NET
        if (hasFileWithExtension(root, ".csproj")) frameworks.add(".NET");

        return frameworks;
    }

    private List<String> detectDatabases(Path root) {
        List<String> databases = new ArrayList<>();

        // Check config files
        String dockerCompose = readSafe(root.resolve("docker-compose.yml"));
        dockerCompose += readSafe(root.resolve("docker-compose.yaml"));

        if (dockerCompose.contains("postgres")) databases.add("PostgreSQL");
        if (dockerCompose.contains("mysql")) databases.add("MySQL");
        if (dockerCompose.contains("mongo")) databases.add("MongoDB");
        if (dockerCompose.contains("redis")) databases.add("Redis");
        if (dockerCompose.contains("neo4j")) databases.add("Neo4j");
        if (dockerCompose.contains("qdrant")) databases.add("Qdrant");
        if (dockerCompose.contains("minio")) databases.add("MinIO");
        if (dockerCompose.contains("elasticsearch")) databases.add("Elasticsearch");

        // Check application config
        String appConfig = readSafe(root.resolve("src/main/resources/application.yml"));
        appConfig += readSafe(root.resolve("src/main/resources/application.properties"));
        appConfig += readSafe(root.resolve("src/main/resources/application.yaml"));

        if (appConfig.contains("postgresql") || appConfig.contains("jdbc:postgresql")) databases.add("PostgreSQL");
        if (appConfig.contains("mysql") || appConfig.contains("jdbc:mysql")) databases.add("MySQL");
        if (appConfig.contains("mongodb")) databases.add("MongoDB");
        if (appConfig.contains("redis")) databases.add("Redis");
        if (appConfig.contains("neo4j")) databases.add("Neo4j");
        if (appConfig.contains("h2")) databases.add("H2");

        return databases.stream().distinct().toList();
    }

    private DockerInfo detectDocker(Path root) {
        boolean hasDockerfile = Files.exists(root.resolve("Dockerfile"));
        boolean hasCompose = Files.exists(root.resolve("docker-compose.yml")) ||
            Files.exists(root.resolve("docker-compose.yaml"));
        boolean hasDockerignore = Files.exists(root.resolve(".dockerignore"));

        List<String> services = new ArrayList<>();
        if (hasCompose) {
            String compose = readSafe(root.resolve("docker-compose.yml"));
            compose += readSafe(root.resolve("docker-compose.yaml"));
            // Extract service names (lines under "services:" key)
            String[] lines = compose.split("\n");
            boolean inServices = false;
            for (String line : lines) {
                if (line.trim().equals("services:")) {
                    inServices = true;
                    continue;
                }
                if (inServices && line.matches("^  [a-zA-Z].*:.*")) {
                    String serviceName = line.trim().replace(":", "").trim();
                    if (!serviceName.isEmpty()) services.add(serviceName);
                }
                if (inServices && !line.startsWith(" ") && !line.startsWith("#") && !line.isBlank()) {
                    inServices = false;
                }
            }
        }

        return DockerInfo.builder()
            .hasDockerfile(hasDockerfile)
            .hasCompose(hasCompose)
            .hasDockerignore(hasDockerignore)
            .services(services)
            .build();
    }

    private List<String> detectKubernetes(Path root) {
        List<String> manifests = new ArrayList<>();

        Path[] k8sDirs = {
            root.resolve("k8s"),
            root.resolve("kubernetes"),
            root.resolve("k8s"),
            root.resolve("deploy"),
            root.resolve("deployment"),
            root.resolve("charts")
        };

        for (Path dir : k8sDirs) {
            if (Files.exists(dir) && Files.isDirectory(dir)) {
                try (Stream<Path> files = Files.walk(dir, 3)) {
                    files.filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.getFileName().toString();
                            return name.endsWith(".yaml") || name.endsWith(".yml");
                        })
                        .forEach(p -> manifests.add(root.relativize(p).toString()));
                } catch (IOException e) {
                    log.debug("K8s scan failed: {}", e.getMessage());
                }
            }
        }

        // Also check for Helm
        if (Files.exists(root.resolve("Chart.yaml"))) {
            manifests.add("Chart.yaml (Helm)");
        }

        return manifests;
    }

    private List<String> detectPackageManagers(Path root) {
        List<String> managers = new ArrayList<>();
        if (Files.exists(root.resolve("pom.xml"))) managers.add("Maven");
        if (Files.exists(root.resolve("build.gradle")) || Files.exists(root.resolve("build.gradle.kts"))) managers.add("Gradle");
        if (Files.exists(root.resolve("package.json"))) managers.add("npm");
        if (Files.exists(root.resolve("yarn.lock"))) managers.add("Yarn");
        if (Files.exists(root.resolve("pnpm-lock.yaml"))) managers.add("pnpm");
        if (Files.exists(root.resolve("requirements.txt"))) managers.add("pip");
        if (Files.exists(root.resolve("Pipfile"))) managers.add("Pipenv");
        if (Files.exists(root.resolve("pyproject.toml"))) managers.add("poetry");
        if (Files.exists(root.resolve("go.mod"))) managers.add("Go Modules");
        if (Files.exists(root.resolve("Cargo.toml"))) managers.add("Cargo");
        if (Files.exists(root.resolve("Gemfile"))) managers.add("Bundler");
        if (Files.exists(root.resolve("composer.json"))) managers.add("Composer");
        return managers;
    }

    private List<String> detectCICD(Path root) {
        List<String> cicd = new ArrayList<>();

        // GitHub Actions
        Path ghDir = root.resolve(".github/workflows");
        if (Files.exists(ghDir) && Files.isDirectory(ghDir)) {
            try (Stream<Path> files = Files.list(ghDir)) {
                files.filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"))
                    .forEach(p -> cicd.add("GitHub Actions: " + p.getFileName().toString()));
            } catch (IOException e) { /* ignore */ }
        }

        // GitLab CI
        if (Files.exists(root.resolve(".gitlab-ci.yml"))) cicd.add("GitLab CI");

        // Jenkins
        if (Files.exists(root.resolve("Jenkinsfile"))) cicd.add("Jenkins");

        // CircleCI
        if (Files.exists(root.resolve(".circleci/config.yml"))) cicd.add("CircleCI");

        // Travis
        if (Files.exists(root.resolve(".travis.yml"))) cicd.add("Travis CI");

        // Azure DevOps
        if (Files.exists(root.resolve("azure-pipelines.yml"))) cicd.add("Azure DevOps");

        return cicd;
    }

    private void createGraphNodes(BootstrapResult result) {
        try {
            String repoId = UUID.randomUUID().toString();
            graphService.createNode("Repository", repoId, Map.of(
                "path", result.path,
                "languages", String.join(", ", result.languages),
                "frameworks", String.join(", ", result.frameworks),
                "databases", String.join(", ", result.databases)
            ));

            // Create technology nodes
            for (String framework : result.frameworks) {
                graphService.createNode("Technology", framework, Map.of(
                    "name", framework,
                    "category", "framework"
                ));
                graphService.createRelationship("Repository", repoId, "Technology", framework, "USES", Map.of());
            }

            for (String db : result.databases) {
                graphService.createNode("Technology", db, Map.of(
                    "name", db,
                    "category", "database"
                ));
                graphService.createRelationship("Repository", repoId, "Technology", db, "USES", Map.of());
            }

            log.info("Created {} graph nodes for repository", result.frameworks.size() + result.databases.size());
        } catch (Exception e) {
            log.warn("Graph node creation failed: {}", e.getMessage());
        }
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }

    private String readSafe(Path path) {
        if (!Files.exists(path)) return "";
        try {
            return Files.readString(path);
        } catch (IOException e) {
            return "";
        }
    }

    private boolean hasFileContaining(Path root, String filename, String content) {
        return readSafe(root.resolve(filename)).contains(content);
    }

    private boolean hasFileWithExtension(Path root, String ext) {
        try (Stream<Path> files = Files.walk(root, 6)) {
            return files.filter(Files::isRegularFile)
                .anyMatch(p -> p.toString().endsWith(ext));
        } catch (IOException e) {
            return false;
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BootstrapResult {
        private String path;
        private String status;
        private String error;
        private java.time.LocalDateTime detectedAt;
        private GitInfo git;
        private List<String> languages;
        private List<String> frameworks;
        private List<String> databases;
        private DockerInfo docker;
        private List<String> kubernetes;
        private List<String> packageManagers;
        private List<String> cicd;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class GitInfo {
        private boolean isGitRepository;
        private String defaultBranch;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DockerInfo {
        private boolean hasDockerfile;
        private boolean hasCompose;
        private boolean hasDockerignore;
        private List<String> services;
    }
}
