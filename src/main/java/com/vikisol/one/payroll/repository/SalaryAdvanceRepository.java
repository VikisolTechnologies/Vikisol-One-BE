package com.vikisol.one.payroll.repository;

import com.vikisol.one.payroll.entity.SalaryAdvance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SalaryAdvanceRepository extends JpaRepository<SalaryAdvance, UUID> {

    List<SalaryAdvance> findByEmployeeId(UUID employeeId);

    List<SalaryAdvance> findByStatus(SalaryAdvance.AdvanceStatus status);
}
