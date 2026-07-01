package com.vikisol.one.project.repository;

import com.vikisol.one.project.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByProjectId(UUID projectId);

    List<Task> findByAssigneeId(UUID assigneeId);

    List<Task> findByProjectIdAndStatus(UUID projectId, Task.Status status);
}
