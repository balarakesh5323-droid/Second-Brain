package com.secondbrain.common.repository;

import com.secondbrain.common.entity.RepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepositoryEntityRepository extends JpaRepository<RepositoryEntity, UUID> {
    Optional<RepositoryEntity> findByUrl(String url);
    Optional<RepositoryEntity> findByName(String name);
    Optional<RepositoryEntity> findByPath(String path);
    List<RepositoryEntity> findByProjectId(UUID projectId);
}
