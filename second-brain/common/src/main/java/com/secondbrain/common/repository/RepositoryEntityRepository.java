package com.secondbrain.common.repository;

import com.secondbrain.common.entity.RepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RepositoryEntityRepository extends JpaRepository<RepositoryEntity, UUID> {
}
