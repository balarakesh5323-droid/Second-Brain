package com.secondbrain.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "project_documents")
public class ProjectDocument extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String fileType; // "DOCUMENT" | "IMAGE"

    @Column(nullable = false)
    private String contentType;

    private Long sizeBytes;

    @Column(nullable = false, length = 1024)
    private String storageKey;

    @Column(length = 2048)
    private String url;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String extractedText;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Project project;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "project_document_tags", joinColumns = @JoinColumn(name = "document_id"))
    @Column(name = "tag")
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    @com.fasterxml.jackson.annotation.JsonProperty("projectId")
    public UUID getProjectId() {
        return project != null ? project.getId() : null;
    }
}
