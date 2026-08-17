package com.secondbrain.common.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "agent_handoffs")
public class AgentHandoff extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AgentSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private RepositoryEntity repository;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    private String task;

    @Column(columnDefinition = "text")
    private String completedItems;

    @Column(columnDefinition = "text")
    private String inProgressItems;

    @Column(columnDefinition = "text")
    private String blockedItems;

    @Column(columnDefinition = "text")
    private String changedFiles;

    @Column(columnDefinition = "text")
    private String nextSteps;

    @Column(columnDefinition = "text")
    private String decisions;

    @Column(columnDefinition = "text")
    private String knownIssues;
}
