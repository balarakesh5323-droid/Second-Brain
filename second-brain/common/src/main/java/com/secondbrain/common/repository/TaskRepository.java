package com.secondbrain.common.repository;

import com.secondbrain.common.entity.Task;
import com.secondbrain.common.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByStatus(TaskStatus status);

    List<Task> findByProjectId(UUID projectId);

    List<Task> findByRepositoryId(UUID repositoryId);

    List<Task> findByRepositoryIdAndStatus(UUID repositoryId, TaskStatus status);

    List<Task> findByStatusAndProjectId(TaskStatus status, UUID projectId);

    List<Task> findByProjectIdAndStatus(UUID projectId, TaskStatus status);
}
