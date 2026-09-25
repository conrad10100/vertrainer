package com.loadedvj.backend.service;

import com.loadedvj.backend.repository.ProgramRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Guards against two "generate next week" requests running for the same program at once -- e.g.
 * a client-side retry after a dropped connection while the first attempt is still running the
 * (30-90s) Claude call server-side. Each method commits in its own transaction (REQUIRES_NEW) so
 * the lock state is visible to a concurrent request immediately, rather than only after the
 * caller's own long-running transaction eventually commits.
 */
@Service
public class ProgramGenerationLockService {

    public static final Duration STALE_AFTER = Duration.ofMinutes(5);

    private final ProgramRepository programRepository;

    public ProgramGenerationLockService(ProgramRepository programRepository) {
        this.programRepository = programRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAcquire(UUID programId) {
        Instant now = Instant.now();
        return programRepository.tryMarkGenerationStarted(programId, now, now.minus(STALE_AFTER)) > 0;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(UUID programId) {
        programRepository.clearGenerationStarted(programId);
    }
}
