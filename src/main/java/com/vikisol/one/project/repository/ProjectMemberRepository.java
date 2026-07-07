package com.vikisol.one.project.repository;

import com.vikisol.one.project.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    List<ProjectMember> findByProjectId(UUID projectId);

    List<ProjectMember> findByProjectIdAndIsActiveTrue(UUID projectId);

    List<ProjectMember> findByEmployeeId(UUID employeeId);

    List<ProjectMember> findByEmployeeIdAndIsActiveTrue(UUID employeeId);

    long countByProjectIdAndIsActiveTrue(UUID projectId);
}
