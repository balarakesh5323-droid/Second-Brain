package com.secondbrain.cli;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Component
@Command(
    name = "brain",
    description = "Second Brain CLI - interact with your knowledge base",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    subcommands = {
        BrainCli.InitCommand.class,
        BrainCli.WatchCommand.class,
        BrainCli.SearchCommand.class,
        BrainCli.AskCommand.class,
        BrainCli.RememberCommand.class,
        BrainCli.ProjectsCommand.class,
        BrainCli.TasksCommand.class,
        BrainCli.DecisionsCommand.class,
        BrainCli.ContextCommand.class,
        BrainCli.StatusCommand.class,
        BrainCli.HandoffCommand.class,
        BrainCli.AttemptsCommand.class,
        BrainCli.ContinuityCommand.class
    }
)
public class BrainCli implements CommandLineRunner, Runnable {

    @Option(names = {"-s", "--server"}, description = "Second Brain server URL", defaultValue = "http://localhost:8080")
    private String serverUrl;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new BrainCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        System.out.println("Second Brain CLI v1.0.0");
        System.out.println("Use 'brain <command> --help' for available commands.");
        System.out.println();
        System.out.println("Available commands:");
        System.out.println("  init        Initialize brain for a repository (detect language, frameworks, DBs)");
        System.out.println("  watch       Watch repository directory & auto-stream agent activity + diffs to Brain");
        System.out.println("  search      Search memories by keyword");
        System.out.println("  ask         Ask a natural language question");
        System.out.println("  remember    Store a new memory");
        System.out.println("  projects    List all projects");
        System.out.println("  tasks       List open tasks");
        System.out.println("  decisions   List recent decisions");
        System.out.println("  context     Assemble full context for a query");
        System.out.println("  status      Check brain health status");
        System.out.println("  handoff     Get latest agent handoff");
        System.out.println("  attempts    Query prior engineering attempts, failed trials, and lessons learned");
        System.out.println("  continuity  Fetch 1-shot cross-agent continuity snapshot for incoming AI tools");
    }

    @Override
    public void run(String... args) throws Exception {
        if (args.length == 0) {
            run();
            return;
        }
        new CommandLine(new BrainCli()).execute(args);
    }

    @Command(name = "init", description = "Initialize brain for a repository (detect language, frameworks, DBs)")
    static class InitCommand implements Runnable {
        @Parameters(index = "0", description = "Path to repository directory", defaultValue = ".")
        private String path;

        @Option(names = {"-n", "--name"}, description = "Project name (default: directory name)")
        private String name;

        @Option(names = {"-s", "--server"}, description = "Server URL", defaultValue = "http://localhost:8080")
        private String serverUrl;

        @Override
        public void run() {
            try {
                java.io.File dir = new java.io.File(path).getCanonicalFile();
                if (!dir.isDirectory()) {
                    System.err.println("Not a directory: " + dir.getAbsolutePath());
                    return;
                }

                String projName = (name != null && !name.isBlank()) ? name : dir.getName();
                System.out.println("Initializing brain for: " + dir.getAbsolutePath());
                System.out.println("Project Name: " + projName);

                // Detect technologies
                java.util.List<String> detected = new java.util.ArrayList<>();
                if (new java.io.File(dir, "pom.xml").exists() || new java.io.File(dir, "build.gradle").exists()) {
                    detected.add("Java");
                }
                if (new java.io.File(dir, "package.json").exists()) {
                    detected.add("Node.js/JavaScript");
                }
                if (new java.io.File(dir, "requirements.txt").exists() || new java.io.File(dir, "pyproject.toml").exists()) {
                    detected.add("Python");
                }
                if (new java.io.File(dir, "Cargo.toml").exists()) {
                    detected.add("Rust");
                }
                if (new java.io.File(dir, "go.mod").exists()) {
                    detected.add("Go");
                }

                System.out.println("Detected technologies: " + (detected.isEmpty() ? "None auto-detected" : String.join(", ", detected)));

                // Call server to create project
                var client = java.net.http.HttpClient.newHttpClient();
                String body = String.format("{\"name\":\"%s\",\"path\":\"%s\",\"description\":\"Initialized from CLI\"}",
                    projName, dir.getAbsolutePath().replace("\\", "\\\\"));

                var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(serverUrl + "/api/v1/projects"))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .build();

                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 || response.statusCode() == 201) {
                    System.out.println("Project created successfully in Second Brain!");
                    System.out.println(formatJson(response.body()));
                } else {
                    System.out.println("Server responded with status " + response.statusCode() + ": " + response.body());
                }
            } catch (Exception e) {
                System.err.println("Error initializing project: " + e.getMessage());
            }
        }
    }

    @Command(name = "watch", description = "Watch directory & auto-stream agent activity/diffs to Second Brain")
    static class WatchCommand implements Runnable {
        @Parameters(index = "0", description = "Path to directory to watch", defaultValue = ".")
        private String watchPath;

        @Option(names = {"-a", "--agent"}, description = "Agent name (e.g. claude-code, codex, cursor)", defaultValue = "claude-code")
        private String agentName;

        @Option(names = {"-s", "--server"}, description = "Server URL", defaultValue = "http://localhost:8080")
        private String serverUrl;

        @Override
        public void run() {
            try {
                java.io.File dir = new java.io.File(watchPath).getCanonicalFile();
                if (!dir.isDirectory()) {
                    System.err.println("Not a directory: " + dir.getAbsolutePath());
                    return;
                }

                System.out.println("Watching: " + dir.getAbsolutePath() + " for agent: " + agentName);
                System.out.println("Autonomous Agent Bridge Active. Streaming file edits & uncommitted diffs to " + serverUrl);
                System.out.println("Press Ctrl+C to stop.");
                System.out.println();

                java.nio.file.WatchService watchService = java.nio.file.FileSystems.getDefault().newWatchService();
                java.nio.file.Path watchDir = dir.toPath();
                watchDir.register(watchService,
                    java.nio.file.StandardWatchEventKinds.ENTRY_CREATE,
                    java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY,
                    java.nio.file.StandardWatchEventKinds.ENTRY_DELETE);

                long debounceMs = 2500;
                long lastEvent = 0;
                java.util.Set<String> pendingChanges = new java.util.LinkedHashSet<>();
                var client = java.net.http.HttpClient.newHttpClient();

                while (true) {
                    java.nio.file.WatchKey key = watchService.take();
                    for (java.nio.file.WatchEvent<?> event : key.pollEvents()) {
                        java.nio.file.Path changed = (java.nio.file.Path) event.context();
                        String changeType = event.kind().name();
                        String relativePath = watchDir.resolve(changed).toString();
                        if (!relativePath.contains(".git") && !relativePath.contains("build") && !relativePath.contains("node_modules")) {
                            pendingChanges.add(changeType + " " + relativePath);
                        }
                    }
                    key.reset();

                    long now = System.currentTimeMillis();
                    if (!pendingChanges.isEmpty() && (now - lastEvent) >= debounceMs) {
                        System.out.println("[" + java.time.LocalTime.now() + "] Auto-capturing " + pendingChanges.size() + " change(s) from " + agentName);

                        // Capture git diff if git repo
                        String diff = "";
                        try {
                            Process p = new ProcessBuilder("git", "diff", "--stat").directory(dir).start();
                            diff = new String(p.getInputStream().readAllBytes());
                        } catch (Exception ignored) {}

                        // Post activity to Second Brain
                        try {
                            String payload = String.format(
                                "{\"agentName\":\"%s\",\"actionType\":\"FILE_EDIT\",\"repositoryPath\":\"%s\",\"notes\":\"%s\",\"workingTreeDiff\":\"%s\"}",
                                agentName,
                                dir.getAbsolutePath().replace("\\", "\\\\"),
                                String.join("; ", pendingChanges).replace("\"", "\\\""),
                                diff.replace("\"", "\\\"").replace("\n", "\\n")
                            );
                            var request = java.net.http.HttpRequest.newBuilder()
                                .uri(java.net.URI.create(serverUrl + "/api/v1/bridge/activity"))
                                .header("Content-Type", "application/json")
                                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload))
                                .build();
                            client.send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
                            System.out.println("  -> Synced to Second Brain Bridge successfully");
                        } catch (Exception err) {
                            System.err.println("  -> Sync warning: " + err.getMessage());
                        }

                        pendingChanges.clear();
                        lastEvent = now;
                    }
                }
            } catch (java.nio.file.ClosedWatchServiceException e) {
                System.out.println("Watch stopped.");
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "search", description = "Search memories by keyword")
    static class SearchCommand implements Runnable {
        @Parameters(index = "0", description = "Search query")
        private String query;

        @Option(names = {"-l", "--limit"}, description = "Max results", defaultValue = "10")
        private int limit;

        @Option(names = {"-s", "--server"}, description = "Server URL", defaultValue = "http://localhost:8080")
        private String serverUrl;

        @Override
        public void run() {
            try {
                var client = java.net.http.HttpClient.newHttpClient();
                var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(serverUrl + "/api/v1/memory/search?q=" + java.net.URLEncoder.encode(query, "UTF-8")))
                    .GET()
                    .build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                System.out.println("Search Results:");
                System.out.println(formatJson(response.body()));
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "ask", description = "Ask a natural language question")
    static class AskCommand implements Runnable {
        @Parameters(index = "0", description = "Question to ask")
        private String question;

        @Option(names = {"-s", "--server"}, description = "Server URL", defaultValue = "http://localhost:8080")
        private String serverUrl;

        @Override
        public void run() {
            try {
                var client = java.net.http.HttpClient.newHttpClient();
                var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(serverUrl + "/api/v1/context/ask?q=" + java.net.URLEncoder.encode(question, "UTF-8")))
                    .GET()
                    .build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                System.out.println("Answer:");
                System.out.println(formatJson(response.body()));
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "remember", description = "Store a new memory")
    static class RememberCommand implements Runnable {
        @Parameters(index = "0", description = "Content of the memory")
        private String content;

        @Option(names = {"-t", "--type"}, description = "Memory type (DECLARATIVE, PROCEDURAL, EPISODIC)", defaultValue = "DECLARATIVE")
        private String type;

        @Option(names = {"-p", "--project"}, description = "Project UUID")
        private String projectId;

        @Option(names = {"-s", "--server"}, description = "Server URL", defaultValue = "http://localhost:8080")
        private String serverUrl;

        @Override
        public void run() {
            try {
                var client = java.net.http.HttpClient.newHttpClient();
                String body = String.format("{\"content\":\"%s\",\"type\":\"%s\",\"scope\":\"GLOBAL\"}",
                    content.replace("\"", "\\\""), type);
                var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(serverUrl + "/api/v1/memory"))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                System.out.println("Memory saved:");
                System.out.println(formatJson(response.body()));
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "projects", description = "List all projects")
    static class ProjectsCommand implements Runnable {
        @Option(names = {"-s", "--server"}, description = "Server URL", defaultValue = "http://localhost:8080")
        private String serverUrl;

        @Override
        public void run() {
            try {
                var client = java.net.http.HttpClient.newHttpClient();
                var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(serverUrl + "/api/v1/projects"))
                    .GET()
                    .build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                System.out.println("Projects:");
                System.out.println(formatJson(response.body()));
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "tasks", description = "List open tasks")
    static class TasksCommand implements Runnable {
        @Option(names = {"-p", "--project"}, description = "Project UUID filter")
        private String projectId;

        @Option(names = {"-s", "--server"}, description = "Server URL", defaultValue = "http://localhost:8080")
        private String serverUrl;

        @Override
        public void run() {
            try {
                var client = java.net.http.HttpClient.newHttpClient();
                String uri = serverUrl + "/api/v1/tasks/open" + (projectId != null ? "?projectId=" + projectId : "");
                var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(uri))
                    .GET()
                    .build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                System.out.println("Open Tasks:");
                System.out.println(formatJson(response.body()));
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "decisions", description = "List recent decisions")
    static class DecisionsCommand implements Runnable {
        @Option(names = {"-p", "--project"}, description = "Project UUID filter")
        private String projectId;

        @Option(names = {"-s", "--server"}, description = "Server URL", defaultValue = "http://localhost:8080")
        private String serverUrl;

        @Override
        public void run() {
            try {
                var client = java.net.http.HttpClient.newHttpClient();
                String uri = serverUrl + "/api/v1/decisions" + (projectId != null ? "?projectId=" + projectId : "");
                var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(uri))
                    .GET()
                    .build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                System.out.println("Decisions:");
                System.out.println(formatJson(response.body()));
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "context", description = "Assemble full context for a query")
    static class ContextCommand implements Runnable {
        @Parameters(index = "0", description = "Query to assemble context for")
        private String query;

        @Option(names = {"-p", "--project"}, description = "Project UUID")
        private String projectId;

        @Option(names = {"-s", "--server"}, description = "Server URL", defaultValue = "http://localhost:8080")
        private String serverUrl;

        @Override
        public void run() {
            try {
                var client = java.net.http.HttpClient.newHttpClient();
                String uri = serverUrl + "/api/v1/context?q=" + java.net.URLEncoder.encode(query, "UTF-8")
                    + (projectId != null ? "&projectId=" + projectId : "");
                var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(uri))
                    .GET()
                    .build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                System.out.println("Context:");
                System.out.println(formatJson(response.body()));
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "status", description = "Check brain health status")
    static class StatusCommand implements Runnable {
        @Option(names = {"-s", "--server"}, description = "Server URL", defaultValue = "http://localhost:8080")
        private String serverUrl;

        @Override
        public void run() {
            try {
                var client = java.net.http.HttpClient.newHttpClient();
                var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(serverUrl + "/actuator/health"))
                    .GET()
                    .build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                System.out.println("Health Status:");
                System.out.println(formatJson(response.body()));
            } catch (Exception e) {
                System.err.println("Error connecting to server: " + e.getMessage());
            }
        }
    }

    @Command(name = "handoff", description = "Get latest agent handoff")
    static class HandoffCommand implements Runnable {
        @Parameters(index = "0", description = "Repository UUID")
        private String repositoryId;

        @Option(names = {"-s", "--server"}, description = "Server URL", defaultValue = "http://localhost:8080")
        private String serverUrl;

        @Override
        public void run() {
            try {
                var client = java.net.http.HttpClient.newHttpClient();
                var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(serverUrl + "/api/v1/handoffs/repository/" + repositoryId + "/latest"))
                    .GET()
                    .build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                System.out.println("Latest Handoff:");
                System.out.println(formatJson(response.body()));
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "attempts", description = "Query prior engineering attempts, failed trials, and lessons learned")
    static class AttemptsCommand implements Runnable {
        @Option(names = {"-r", "--repo"}, description = "Repository UUID filter")
        private String repositoryId;

        @Option(names = {"-s", "--server"}, description = "Server URL", defaultValue = "http://localhost:8080")
        private String serverUrl;

        @Override
        public void run() {
            try {
                var client = java.net.http.HttpClient.newHttpClient();
                String uri = serverUrl + "/api/v1/bridge/attempts" + (repositoryId != null ? "/repository/" + repositoryId : "");
                var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(uri))
                    .GET()
                    .build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                System.out.println("Engineering Attempts & Lessons Learned:");
                System.out.println(formatJson(response.body()));
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "continuity", description = "Fetch 1-shot cross-agent continuity snapshot for incoming AI tools")
    static class ContinuityCommand implements Runnable {
        @Parameters(index = "0", description = "Repository path or UUID", defaultValue = ".")
        private String target;

        @Option(names = {"-s", "--server"}, description = "Server URL", defaultValue = "http://localhost:8080")
        private String serverUrl;

        @Override
        public void run() {
            try {
                var client = java.net.http.HttpClient.newHttpClient();
                java.io.File dir = new java.io.File(target);
                String lookup = dir.exists() ? dir.getCanonicalPath() : target;
                String uri = serverUrl + "/api/v1/bridge/continuity?repo=" + java.net.URLEncoder.encode(lookup, "UTF-8");
                var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(uri))
                    .GET()
                    .build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                System.out.println("Multi-Agent Continuity Snapshot:");
                System.out.println(formatJson(response.body()));
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    private static String formatJson(String json) {
        if (json == null || json.isBlank()) return "(empty)";
        try {
            var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var tree = objectMapper.readTree(json);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree);
        } catch (Exception e) {
            return json;
        }
    }
}
