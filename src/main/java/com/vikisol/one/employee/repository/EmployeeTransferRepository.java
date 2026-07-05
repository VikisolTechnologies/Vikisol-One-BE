package com.vikisol.one.employee.repository;

import com.vikisol.one.employee.entity.EmployeeTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmployeeTransferRepository extends JpaRepository<EmployeeTransfer, UUID> {

    List<EmployeeTransfer> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);
}
