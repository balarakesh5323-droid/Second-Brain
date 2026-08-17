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
        BrainCli.HandoffCommand.class
    }
)
public class BrainCli implements CommandLineRunner {

    @Option(names = {"-s", "--server"}, description = "Second Brain server URL", defaultValue = "http://localhost:8080")
    private String serverUrl;

    @Override
    public void run(String... args) throws Exception {
        if (args.length == 0) {
            System.out.println("Second Brain CLI v1.0.0");
            System.out.println("Use 'brain <command> --help' for available commands.");
            System.out.println();
            System.out.println("Available commands:");
            System.out.println("  init       Initialize brain for a repository (detect language, frameworks, DBs)");
            System.out.println("  watch      Watch directory for changes and index them");
            System.out.println("  search     Search memories by keyword");
            System.out.println("  ask        Ask a natural language question");
            System.out.println("  remember   Store a new memory");
            System.out.println("  projects   List all projects");
            System.out.println("  tasks      List open tasks");
            System.out.println("  decisions  List recent decisions");
            System.out.println("  context    Assemble full context for a query");
            System.out.println("  status     Check brain health status");
            System.out.println("  handoff    Get latest agent handoff");
        }
    }

    @Command(name = "init", description = "Initialize brain for a repository (detect language, frameworks, databases, Docker, CI/CD)")
    static class InitCommand implements Runnable {
        @Parameters(index = "0", description = "Path to repository", defaultValue = ".")
        private String repoPath;

        @Option(names = {"-s", "--server"}, description = "Server URL", defaultValue = "http://localhost:8080")
        private String serverUrl;

        @Override
        public void run() {
            try {
                java.io.File dir = new java.io.File(repoPath).getCanonicalFile();
                System.out.println("Initializing brain for: " + dir.getAbsolutePath());
                System.out.println();

                var client = java.net.http.HttpClient.newHttpClient();
                var url = serverUrl + "/api/v1/repositories/bootstrap?path=" +
                    java.net.URLEncoder.encode(dir.getAbsolutePath(), "UTF-8");
                var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                    .build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    System.out.println(formatJson(response.body()));
                } else {
                    System.err.println("Bootstrap failed (HTTP " + response.statusCode() + "): " + response.body());
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "watch", description = "Watch a directory for file changes and index them (with debouncing)")
    static class WatchCommand implements Runnable {
        @Parameters(index = "0", description = "Directory to watch", defaultValue = ".")
        private String watchPath;

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

                System.out.println("Watching: " + dir.getAbsolutePath());
                System.out.println("Press Ctrl+C to stop.");
                System.out.println();

                java.nio.file.WatchService watchService = java.nio.file.FileSystems.getDefault().newWatchService();
                java.nio.file.Path watchDir = dir.toPath();
                watchDir.register(watchService,
                    java.nio.file.StandardWatchEventKinds.ENTRY_CREATE,
                    java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY,
                    java.nio.file.StandardWatchEventKinds.ENTRY_DELETE);

                long debounceMs = 2000;
                long lastEvent = 0;
                java.util.Set<String> pendingChanges = new java.util.LinkedHashSet<>();

                while (true) {
                    java.nio.file.WatchKey key = watchService.take();
                    for (java.nio.file.WatchEvent<?> event : key.pollEvents()) {
                        java.nio.file.Path changed = (java.nio.file.Path) event.context();
                        String changeType = event.kind().name();
                        String relativePath = watchDir.resolve(changed).toString();
                        pendingChanges.add(changeType + " " + relativePath);
                    }
                    key.reset();

                    long now = System.currentTimeMillis();
                    if (!pendingChanges.isEmpty() && (now - lastEvent) >= debounceMs) {
                        System.out.println("[" + java.time.LocalTime.now() + "] Changes detected:");
                        for (String change : pendingChanges) {
                            System.out.println("  " + change);
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
                System.out.println(formatJson(response.body()));
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "ask", description = "Ask a natural language question")
    static class AskCommand implements Runnable {
        @Parameters(index = "0", description = "Question")
        private String question;

        @Option(names = {"-s", "--server"}, description = "Server URL", defaultValue = "http://localhost:8080")
        private String serverUrl;

        @Override
        public void run() {
            try {
                var client = java.net.http.HttpClient.newHttpClient();
                var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(serverUrl + "/api/v1/memory/search?q=" + java.net.URLEncoder.encode(question, "UTF-8")))
                    .GET()
                    .build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                System.out.println("Question: " + question);
                System.out.println("Results:");
                System.out.println(formatJson(response.body()));
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "remember", description = "Store a new memory")
    static class RememberCommand implements Runnable {
        @Parameters(index = "0", description = "Memory content")
        private String content;

        @Option(names = {"-t", "--type"}, description = "Memory type", defaultValue = "DECLARATIVE")
        private String type;

        @Option(names = {"--scope"}, description = "Scope (GLOBAL, PROJECT, REPOSITORY)", defaultValue = "GLOBAL")
        private String scope;

        @Option(names = {"-s", "--server"}, description = "Server URL", defaultValue = "http://localhost:8080")
        private String serverUrl;

        @Override
        public void run() {
            try {
                var client = java.net.http.HttpClient.newHttpClient();
                var json = String.format(
                    "{\"content\":\"%s\",\"type\":\"%s\",\"scope\":\"%s\"}",
                    content.replace("\"", "\\\""), type, scope);
                var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(serverUrl + "/api/v1/memory"))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                    .build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                System.out.println("Memory stored: " + formatJson(response.body()));
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
        @Option(names = {"-s", "--server"}, description = "Server URL", defaultValue = "http://localhost:8080")
        private String serverUrl;

        @Override
        public void run() {
            try {
                var client = java.net.http.HttpClient.newHttpClient();
                var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(serverUrl + "/api/v1/tasks/open"))
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
        @Option(names = {"-s", "--server"}, description = "Server URL", defaultValue = "http://localhost:8080")
        private String serverUrl;

        @Override
        public void run() {
            try {
                var client = java.net.http.HttpClient.newHttpClient();
                var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(serverUrl + "/api/v1/decisions/recent"))
                    .GET()
                    .build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                System.out.println("Recent Decisions:");
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

        @Option(names = {"--project"}, description = "Project UUID to scope results")
        private String projectId;

        @Option(names = {"--repo"}, description = "Repository UUID to scope results")
        private String repositoryId;

        @Option(names = {"-s", "--server"}, description = "Server URL", defaultValue = "http://localhost:8080")
        private String serverUrl;

        @Override
        public void run() {
            try {
                var client = java.net.http.HttpClient.newHttpClient();
                var searchUrl = serverUrl + "/api/v1/memory/search?q=" + java.net.URLEncoder.encode(query, "UTF-8");
                var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(searchUrl))
                    .GET()
                    .build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                System.out.println("Context for: " + query);
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
                    .uri(java.net.URI.create(serverUrl + "/api/v1/health/doctor"))
                    .GET()
                    .build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                System.out.println("Brain Health:");
                System.out.println(formatJson(response.body()));
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "handoff", description = "Get latest agent handoff for a repository")
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
