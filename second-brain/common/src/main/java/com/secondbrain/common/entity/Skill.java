package com.secondbrain.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "skills")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Skill extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    private String version;

    private Double confidence;

    @ElementCollection
    @CollectionTable(name = "skill_triggers", joinColumns = @JoinColumn(name = "skill_id"))
    @Builder.Default
    private Set<String> triggers = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "skill_knowledge", joinColumns = @JoinColumn(name = "skill_id"))
    @Builder.Default
    private Set<String> knowledge = new HashSet<>();

    private String scope;

    private Integer usageCount;

    private LocalDateTime lastUsedAt;
}
