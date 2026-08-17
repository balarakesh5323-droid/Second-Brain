package com.secondbrain.common.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "agent_attempts")
public class AgentAttempt extends BaseEntity {

    @Column(nullable = false)
    private String agentName; // e.g. "claude-code", "codex", "cursor", "antigravity"

    @Column(columnDefinition = "TEXT", nullable = false)
    private String taskDescription;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String approach;

    @Column(length = 32, nullable = false)
    @Builder.Default
    private String status = "IN_PROGRESS"; // "SUCCESS", "FAILURE", "ABORTED", "IN_PROGRESS", "SUPERSEDED"

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "agent_attempt_files", joinColumns = @JoinColumn(name = "attempt_id"))
    @Column(name = "file_path")
    @Builder.Default
    private List<String> filesChanged = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "agent_attempt_commands", joinColumns = @JoinColumn(name = "attempt_id"))
    @Column(name = "command")
    @Builder.Default
    private List<String> commandsExecuted = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String workingTreeDiff;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(columnDefinition = "TEXT")
    private String lessonLearned;

    @Column(columnDefinition = "uuid")
    private UUID supersededBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    @JsonIgnore
    private AgentSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    @JsonIgnore
    private RepositoryEntity repository;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    @JsonIgnore
    private Project project;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "agent_attempt_tags", joinColumns = @JoinColumn(name = "attempt_id"))
    @Column(name = "tag")
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    @JsonProperty("sessionId")
    public UUID getSessionId() {
        return session != null ? session.getId() : null;
    }

    @JsonProperty("repositoryId")
    public UUID getRepositoryId() {
        return repository != null ? repository.getId() : null;
    }

    @JsonProperty("projectId")
    public UUID getProjectId() {
        return project != null ? project.getId() : null;
    }
}
