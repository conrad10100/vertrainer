package com.loadedvj.backend.repository;

import com.loadedvj.backend.domain.Program;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ProgramRepository extends JpaRepository<Program, UUID> {
    Optional<Program> findByUserIdAndActiveTrue(UUID userId);

    /**
     * Atomically claims the generation lock for a program: succeeds (returns 1) only if no
     * generation is currently marked in progress, or the mark is older than {@code staleBefore}
     * (a previous attempt crashed without clearing it). Doing this as a single conditional UPDATE
     * -- rather than a read-then-write -- is what makes it race-safe against a concurrent request.
     */
    @Modifying
    @Query("update Program p set p.generationStartedAt = :now where p.id = :id "
        + "and (p.generationStartedAt is null or p.generationStartedAt < :staleBefore)")
    int tryMarkGenerationStarted(@Param("id") UUID id, @Param("now") Instant now,
                                  @Param("staleBefore") Instant staleBefore);

    @Modifying
    @Query("update Program p set p.generationStartedAt = null where p.id = :id")
    void clearGenerationStarted(@Param("id") UUID id);
}
