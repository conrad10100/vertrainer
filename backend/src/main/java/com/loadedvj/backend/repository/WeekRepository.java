package com.loadedvj.backend.repository;

import com.loadedvj.backend.domain.Week;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WeekRepository extends JpaRepository<Week, UUID> {
    Optional<Week> findTopByProgramIdOrderByWeekNumberDesc(UUID programId);

    List<Week> findByProgramIdOrderByWeekNumberDesc(UUID programId, Pageable pageable);
}
