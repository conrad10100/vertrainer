package com.loadedvj.backend.repository;

import com.loadedvj.backend.domain.Program;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProgramRepository extends JpaRepository<Program, UUID> {
    Optional<Program> findByUserIdAndActiveTrue(UUID userId);
}
