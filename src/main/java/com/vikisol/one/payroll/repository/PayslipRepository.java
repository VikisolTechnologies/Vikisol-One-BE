package com.vikisol.one.payroll.repository;

import com.vikisol.one.payroll.entity.Payslip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayslipRepository extends JpaRepository<Payslip, UUID> {

    Optional<Payslip> findByEmployeeIdAndMonthAndYear(UUID employeeId, int month, int year);

    List<Payslip> findByMonthAndYear(int month, int year);

    Page<Payslip> findByEmployeeIdOrderByYearDescMonthDesc(UUID employeeId, Pageable pageable);

    Page<Payslip> findAllByOrderByYearDescMonthDesc(Pageable pageable);

    List<Payslip> findByMonthAndYearAndStatus(int month, int year, Payslip.PayslipStatus status);

    long countByMonthAndYear(int month, int year);
}
