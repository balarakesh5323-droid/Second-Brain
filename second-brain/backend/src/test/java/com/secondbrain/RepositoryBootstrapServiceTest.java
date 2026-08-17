package com.secondbrain;

import com.secondbrain.service.RepositoryBootstrapService;
import com.secondbrain.service.RepositoryBootstrapService.BootstrapResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RepositoryBootstrapServiceTest {

    @Autowired
    private RepositoryBootstrapService bootstrapService;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("bootstrap detects Java/Maven/Spring Boot project")
    void detectsJavaProject() throws IOException {
        // Create pom.xml with Spring Boot
        Files.writeString(tempDir.resolve("pom.xml"),
            "<project><dependencies><dependency><groupId>org.springframework.boot</groupId>" +
            "<artifactId>spring-boot-starter-web</artifactId></dependency></dependencies></project>");

        // Create Java files
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("App.java"), "package com.example; public class App {}");
        Files.writeString(srcDir.resolve("Controller.java"), "package com.example; public class Controller {}");

        BootstrapResult result = bootstrapService.bootstrap(tempDir.toString());

        assertEquals("bootstrapped", result.getStatus());
        assertTrue(result.getFrameworks().contains("Maven"), "Should detect Maven");
        assertTrue(result.getFrameworks().contains("Spring Boot"), "Should detect Spring Boot");
        assertTrue(result.getFrameworks().contains("Java"), "Should detect Java");
        assertTrue(result.getLanguages().contains("Java"), "Should detect Java language");
    }

    @Test
    @DisplayName("bootstrap detects Node.js/React project")
    void detectsNodeProject() throws IOException {
        Files.writeString(tempDir.resolve("package.json"),
            "{\"name\":\"test\",\"dependencies\":{\"react\":\"18.0.0\",\"tailwindcss\":\"3.0.0\"}}");

        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("index.tsx"), "export const App = () => {}");
        Files.writeString(srcDir.resolve("App.tsx"), "export const App = () => {}");

        BootstrapResult result = bootstrapService.bootstrap(tempDir.toString());

        assertEquals("bootstrapped", result.getStatus());
        assertTrue(result.getFrameworks().contains("Node.js"));
        assertTrue(result.getFrameworks().contains("React"));
        assertTrue(result.getFrameworks().contains("Tailwind CSS"));
    }

    @Test
    @DisplayName("bootstrap detects Docker Compose services")
    void detectsDocker() throws IOException {
        Files.writeString(tempDir.resolve("Dockerfile"), "FROM openjdk:21");
        Files.writeString(tempDir.resolve("docker-compose.yml"),
            "services:\n  api:\n    image: app\n  redis:\n    image: redis\n  postgres:\n    image: postgres\n");

        BootstrapResult result = bootstrapService.bootstrap(tempDir.toString());

        assertTrue(result.getDocker().isHasDockerfile());
        assertTrue(result.getDocker().isHasCompose());
        assertTrue(result.getDocker().getServices().contains("api"));
        assertTrue(result.getDocker().getServices().contains("redis"));
        assertTrue(result.getDocker().getServices().contains("postgres"));
    }

    @Test
    @DisplayName("bootstrap detects databases from config")
    void detectsDatabases() throws IOException {
        Path configDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("application.yml"),
            "spring:\n  datasource:\n    url: jdbc:postgresql://localhost:5432/db\n  data:\n    redis:\n      host: localhost\n  neo4j:\n    uri: bolt://localhost:7687\n");

        BootstrapResult result = bootstrapService.bootstrap(tempDir.toString());

        assertTrue(result.getDatabases().contains("PostgreSQL"));
        assertTrue(result.getDatabases().contains("Redis"));
        assertTrue(result.getDatabases().contains("Neo4j"));
    }

    @Test
    @DisplayName("bootstrap detects CI/CD pipelines")
    void detectsCICD() throws IOException {
        Path ghDir = tempDir.resolve(".github/workflows");
        Files.createDirectories(ghDir);
        Files.writeString(ghDir.resolve("ci.yml"), "name: CI\non: push");
        Files.writeString(ghDir.resolve("deploy.yml"), "name: Deploy\non: release");

        BootstrapResult result = bootstrapService.bootstrap(tempDir.toString());

        assertFalse(result.getCicd().isEmpty());
        assertTrue(result.getCicd().stream().anyMatch(s -> s.contains("ci.yml")));
        assertTrue(result.getCicd().stream().anyMatch(s -> s.contains("deploy.yml")));
    }

    @Test
    @DisplayName("bootstrap detects package managers")
    void detectsPackageManagers() throws IOException {
        Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'java' }");
        Files.writeString(tempDir.resolve("package.json"), "{\"name\":\"test\"}");

        BootstrapResult result = bootstrapService.bootstrap(tempDir.toString());

        assertTrue(result.getPackageManagers().contains("Gradle"));
        assertTrue(result.getPackageManagers().contains("npm"));
    }

    @Test
    @DisplayName("bootstrap returns error for non-existent path")
    void handlesNonExistentPath() {
        BootstrapResult result = bootstrapService.bootstrap("/nonexistent/path");

        assertEquals("error", result.getStatus());
        assertNotNull(result.getError());
    }
}
