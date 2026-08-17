package com.secondbrain.service;

import com.secondbrain.common.entity.Task;
import com.secondbrain.common.enums.TaskStatus;
import com.secondbrain.common.repository.TaskRepository;
import com.secondbrain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;

    public List<Task> getAll() {
        return taskRepository.findAll();
    }

    public Task getById(UUID id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    public List<Task> getOpen() {
        return taskRepository.findByStatus(TaskStatus.OPEN);
    }

    public List<Task> getByProject(UUID projectId) {
        return taskRepository.findByProjectId(projectId);
    }

    public List<Task> getByStatus(TaskStatus status) {
        return taskRepository.findByStatus(status);
    }

    @Transactional
    public Task create(Task task) {
        if (task.getStatus() == null) {
            task.setStatus(TaskStatus.OPEN);
        }
        return taskRepository.save(task);
    }

    @Transactional
    public Task updateStatus(UUID id, TaskStatus status) {
        Task task = getById(id);
        task.setStatus(status);
        if (status == TaskStatus.COMPLETED) {
            task.setCompletedAt(LocalDateTime.now());
        }
        return taskRepository.save(task);
    }

    @Transactional
    public void delete(UUID id) {
        Task task = getById(id);
        taskRepository.delete(task);
    }
}
