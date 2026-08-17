package com.secondbrain.service;

import com.secondbrain.common.dto.MemoryDto;
import com.secondbrain.common.entity.Memory;
import com.secondbrain.common.entity.Project;
import com.secondbrain.common.entity.RepositoryEntity;
import com.secondbrain.common.enums.MemoryType;
import com.secondbrain.common.repository.MemoryRepository;
import com.secondbrain.common.repository.ProjectRepository;
import com.secondbrain.common.repository.RepositoryEntityRepository;
import com.secondbrain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemoryService {

    private final MemoryRepository memoryRepository;
    private final ProjectRepository projectRepository;
    private final RepositoryEntityRepository repositoryEntityRepository;

    @Transactional
    public Memory create(MemoryDto dto) {
        Memory memory = Memory.builder()
                .content(dto.getContent())
                .type(dto.getType())
                .scope(dto.getScope())
                .status(dto.getStatus())
                .confidence(dto.getConfidence())
                .importance(dto.getImportance())
                .observationCount(dto.getObservationCount() != null ? dto.getObservationCount() : 1)
                .tags(dto.getTags())
                .sourceType(dto.getSourceType())
                .sourceId(dto.getSourceId())
                .sourceUrl(dto.getSourceUrl())
                .sourceFile(dto.getSourceFile())
                .lineStart(dto.getLineStart())
                .lineEnd(dto.getLineEnd())
                .sourceCommit(dto.getSourceCommit())
                .sourceAgent(dto.getSourceAgent())
                .sourceSession(dto.getSourceSession())
                .firstSeenAt(dto.getFirstSeenAt() != null ? dto.getFirstSeenAt() : LocalDateTime.now())
                .lastSeenAt(dto.getLastSeenAt() != null ? dto.getLastSeenAt() : LocalDateTime.now())
                .build();

        if (dto.getProjectId() != null) {
            Project project = projectRepository.findById(dto.getProjectId())
                    .orElseThrow(() -> new ResourceNotFoundException("Project", dto.getProjectId()));
            memory.setProject(project);
        }

        if (dto.getRepositoryId() != null) {
            RepositoryEntity repository = repositoryEntityRepository.findById(dto.getRepositoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Repository", dto.getRepositoryId()));
            memory.setRepository(repository);
        }

        return memoryRepository.save(memory);
    }

    public Memory getById(UUID id) {
        return memoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Memory", id));
    }

    public List<Memory> getAll() {
        return memoryRepository.findAll();
    }

    public List<Memory> getByProject(UUID projectId) {
        return memoryRepository.findByProjectId(projectId);
    }

    public List<Memory> getByRepository(UUID repositoryId) {
        return memoryRepository.findByRepositoryId(repositoryId);
    }

    public List<Memory> getByType(MemoryType type) {
        return memoryRepository.findByType(type);
    }

    public List<Memory> search(String query) {
        return memoryRepository.findByContentContainingIgnoreCase(query);
    }

    @Transactional
    public Memory update(UUID id, MemoryDto dto) {
        Memory memory = getById(id);

        if (dto.getContent() != null) memory.setContent(dto.getContent());
        if (dto.getType() != null) memory.setType(dto.getType());
        if (dto.getScope() != null) memory.setScope(dto.getScope());
        if (dto.getStatus() != null) memory.setStatus(dto.getStatus());
        if (dto.getConfidence() != null) memory.setConfidence(dto.getConfidence());
        if (dto.getImportance() != null) memory.setImportance(dto.getImportance());
        if (dto.getTags() != null) memory.setTags(dto.getTags());
        if (dto.getSourceType() != null) memory.setSourceType(dto.getSourceType());
        if (dto.getSourceUrl() != null) memory.setSourceUrl(dto.getSourceUrl());
        if (dto.getSourceFile() != null) memory.setSourceFile(dto.getSourceFile());
        if (dto.getLineStart() != null) memory.setLineStart(dto.getLineStart());
        if (dto.getLineEnd() != null) memory.setLineEnd(dto.getLineEnd());
        if (dto.getSourceCommit() != null) memory.setSourceCommit(dto.getSourceCommit());
        if (dto.getSourceAgent() != null) memory.setSourceAgent(dto.getSourceAgent());

        if (dto.getProjectId() != null) {
            Project project = projectRepository.findById(dto.getProjectId())
                    .orElseThrow(() -> new ResourceNotFoundException("Project", dto.getProjectId()));
            memory.setProject(project);
        }

        if (dto.getRepositoryId() != null) {
            RepositoryEntity repository = repositoryEntityRepository.findById(dto.getRepositoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Repository", dto.getRepositoryId()));
            memory.setRepository(repository);
        }

        return memoryRepository.save(memory);
    }

    @Transactional
    public void delete(UUID id) {
        Memory memory = getById(id);
        memoryRepository.delete(memory);
    }

    @Transactional
    public Memory incrementObservation(UUID id) {
        Memory memory = getById(id);
        memory.setObservationCount(memory.getObservationCount() + 1);
        memory.setLastSeenAt(LocalDateTime.now());
        return memoryRepository.save(memory);
    }
}
