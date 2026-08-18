package com.secondbrain.common.entity;

import com.secondbrain.common.enums.MemoryScope;
import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.enums.MemoryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "memories", uniqueConstraints = {
    @UniqueConstraint(name = "uk_memory_key", columnNames = {"memory_key"})
})
public class Memory extends BaseEntity {

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private MemoryType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private MemoryScope scope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private MemoryStatus status;

    private Double confidence;

    private Double importance;

    @Column(columnDefinition = "integer default 1")
    @Builder.Default
    private Integer observationCount = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private RepositoryEntity repository;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "memory_tags", joinColumns = @JoinColumn(name = "memory_id"))
    @Column(name = "tag")
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    private String sourceType;

    @Column(columnDefinition = "uuid")
    private java.util.UUID sourceId;

    private String sourceUrl;

    private String sourceFile;

    private Integer lineStart;

    private Integer lineEnd;

    private String sourceCommit;

    private String sourceAgent;

    @Column(columnDefinition = "uuid")
    private java.util.UUID sourceSession;

    private LocalDateTime firstSeenAt;

    private LocalDateTime lastSeenAt;

    private LocalDateTime lastConfirmedAt;

    @Column(length = 64)
    @Builder.Default
    private String provenanceSource = "DEVELOPER_EXPLICIT"; // DEVELOPER_EXPLICIT, AGENT_EXPERIENCE, GIT_COMMIT, TEST_EXECUTION, AST_ANALYSIS

    @Column(columnDefinition = "integer default 1")
    @Builder.Default
    private Integer evidenceCount = 1;

    @Column(name = "memory_key", unique = true, length = 255)
    private String memoryKey;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "memory_evidence_sources", joinColumns = @JoinColumn(name = "memory_id"))
    @Column(name = "evidence_source")
    @Builder.Default
    private Set<String> evidenceSources = new HashSet<>();

    @Column(columnDefinition = "uuid")
    private java.util.UUID supersededBy;

    private LocalDateTime supersededAt;

    @Column(columnDefinition = "text")
    private String historicalContext;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "memory_audit_logs", joinColumns = @JoinColumn(name = "memory_id"))
    @Column(name = "audit_entry", columnDefinition = "text")
    @Builder.Default
    private List<String> auditLog = new ArrayList<>();
}
