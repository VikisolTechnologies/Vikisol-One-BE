package com.vikisol.one.employee.repository;

import com.vikisol.one.employee.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByEmployeeId(String employeeId);

    Optional<Employee> findByUserId(UUID userId);

    Page<Employee> findByDepartmentId(UUID departmentId, Pageable pageable);

    List<Employee> findByEmploymentStatus(Employee.EmploymentStatus status);

    List<Employee> findByReportingManagerId(UUID reportingManagerId);

    Page<Employee> findByIsActiveTrue(Pageable pageable);

    @Query("SELECT e FROM Employee e WHERE LOWER(e.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Employee> searchByName(@Param("query") String query, Pageable pageable);

    long countByDepartmentId(UUID departmentId);

    long countByEmploymentStatus(Employee.EmploymentStatus status);

    @Query("SELECT e FROM Employee e WHERE e.isActive = true AND e.user IS NOT NULL AND e.user.role IN ('MANAGER','HR_MANAGER','CEO','ADMIN')")
    List<Employee> findAllManagers();

    List<Employee> findByIsActiveTrueAndDateOfJoining(java.time.LocalDate dateOfJoining);

    List<Employee> findByIsActiveTrueAndLifecycleStatusAndProbationEndDateBetween(
            Employee.LifecycleStatus lifecycleStatus, java.time.LocalDate from, java.time.LocalDate to);

    List<Employee> findByIsActiveTrueAndLifecycleStatusAndProbationEndDateBefore(
            Employee.LifecycleStatus lifecycleStatus, java.time.LocalDate date);

    List<Employee> findByIsActiveTrueAndLifecycleStatus(Employee.LifecycleStatus lifecycleStatus);

    boolean existsByEmployeeId(String employeeId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPersonalEmailIgnoreCase(String personalEmail);

    boolean existsByPersonalMobile(String personalMobile);

    boolean existsByPanNumberIgnoreCase(String panNumber);

    boolean existsByAadharNumber(String aadharNumber);

    boolean existsByUanNumber(String uanNumber);

    boolean existsByPfNumber(String pfNumber);
}
