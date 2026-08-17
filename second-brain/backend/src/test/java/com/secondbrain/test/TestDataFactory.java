package com.secondbrain.test;

import com.secondbrain.common.entity.*;
import com.secondbrain.common.enums.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public class TestDataFactory {

    public static Agent createAgent(String name) {
        return Agent.builder()
            .name(name)
            .type("test-agent")
            .model("test-model")
            .capabilities(new HashSet<>(Set.of("coding", "testing")))
            .build();
    }

    public static Project createProject(String name) {
        return Project.builder()
            .name(name)
            .description("Test project: " + name)
            .path("/test/" + name)
            .status("active")
            .build();
    }

    public static RepositoryEntity createRepository(String name, Project project) {
        return RepositoryEntity.builder()
            .name(name)
            .url("https://github.com/test/" + name)
            .path("/test/repos/" + name)
            .defaultBranch("main")
            .primaryLanguage("Java")
            .project(project)
            .build();
    }

    public static Memory createMemory(String content, MemoryType type, MemoryScope scope) {
        return Memory.builder()
            .content(content)
            .type(type)
            .scope(scope)
            .status(MemoryStatus.NEW)
            .confidence(0.75)
            .importance(0.5)
            .observationCount(1)
            .tags(new HashSet<>(Set.of("test", "e2e")))
            .firstSeenAt(LocalDateTime.now())
            .lastSeenAt(LocalDateTime.now())
            .build();
    }

    public static Decision createDecision(String title, String description) {
        return Decision.builder()
            .title(title)
            .description(description)
            .rationale("Test rationale")
            .status("active")
            .tags(new HashSet<>(Set.of("architecture", "test")))
            .build();
    }

    public static Task createTask(String title, String description) {
        return Task.builder()
            .title(title)
            .description(description)
            .status(TaskStatus.OPEN)
            .priority(3)
            .tags(new HashSet<>(Set.of("feature", "test")))
            .build();
    }

    public static Technology createTechnology(String name, String category) {
        return Technology.builder()
            .name(name)
            .category(category)
            .version("1.0.0")
            .description("Test technology: " + name)
            .build();
    }

    public static Skill createSkill(String name) {
        return Skill.builder()
            .name(name)
            .description("Test skill: " + name)
            .version("1")
            .confidence(0.8)
            .triggers(new HashSet<>(Set.of("test", name.toLowerCase())))
            .knowledge(new HashSet<>(Set.of("testing", "e2e")))
            .scope("global")
            .usageCount(0)
            .lastUsedAt(LocalDateTime.now())
            .build();
    }
}
