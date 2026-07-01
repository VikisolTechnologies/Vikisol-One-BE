package com.vikisol.one.settings.repository;

import com.vikisol.one.settings.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

    List<Holiday> findByYear(int year);

    List<Holiday> findByDateBetween(LocalDate start, LocalDate end);

    List<Holiday> findByYearAndType(int year, Holiday.HolidayType type);

    Optional<Holiday> findByDate(LocalDate date);
}
