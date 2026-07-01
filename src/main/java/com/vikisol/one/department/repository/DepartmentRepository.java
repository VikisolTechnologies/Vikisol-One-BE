package com.vikisol.one.department.repository;

import com.vikisol.one.department.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    Optional<Department> findByCode(String code);

    List<Department> findByNameContainingIgnoreCase(String name);

    List<Department> findByIsActiveTrue();
}
