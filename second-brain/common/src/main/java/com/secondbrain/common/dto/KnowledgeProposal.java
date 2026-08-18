package com.secondbrain.common.dto;

import com.secondbrain.common.enums.MemoryStatus;
import com.secondbrain.common.enums.MemoryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeProposal {

    private String memoryKey;
    private String knowledge;
    private MemoryType type;
    private MemoryStatus status;
    private Double confidence;
    private String reasoning;
    private String projectKey;

    @Builder.Default
    private Set<String> evidenceSources = new HashSet<>();

    @Builder.Default
    private Set<String> supersedesMemoryKeys = new HashSet<>();

    @Builder.Default
    private Set<String> suggestedTags = new HashSet<>();

    @Builder.Default
    private List<AgentProvenance> provenances = new ArrayList<>();
}
