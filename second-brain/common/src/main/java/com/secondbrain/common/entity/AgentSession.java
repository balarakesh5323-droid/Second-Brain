package com.secondbrain.common.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "agent_sessions")
public class AgentSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private RepositoryEntity repository;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    private String task;

    @Column(nullable = false)
    @Builder.Default
    private String status = "active";

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "event_sequence", nullable = false)
    @Builder.Default
    private Long eventSequence = 0L;

    @Column(name = "parent_session_id")
    private java.util.UUID parentSessionId;

    @Column(name = "inherited_from_agent")
    private String inheritedFromAgent;

    @Column(name = "handoff_reason")
    private String handoffReason;
}
