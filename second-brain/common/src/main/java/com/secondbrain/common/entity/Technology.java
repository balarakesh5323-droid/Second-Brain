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
@Table(name = "technologies")
public class Technology extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category;

    private String version;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 32)
    @Builder.Default
    private String experienceLevel = "BEGINNER"; // BEGINNER, INTERMEDIATE, ADVANCED, EXPERT, CORE

    @Builder.Default
    private Double confidence = 0.5;

    @Column(columnDefinition = "integer default 1")
    @Builder.Default
    private Integer observationCount = 1;

    @Column(columnDefinition = "integer default 1")
    @Builder.Default
    private Integer projectCount = 1;

    private LocalDateTime lastUsedAt;
}
