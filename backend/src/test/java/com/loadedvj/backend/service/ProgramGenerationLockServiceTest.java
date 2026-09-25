package com.loadedvj.backend.service;

import com.loadedvj.backend.domain.Program;
import com.loadedvj.backend.repository.ProgramRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the lock against a real database (not mocks) because what's under test is whether
 * the atomic conditional UPDATE actually prevents two concurrent callers from both acquiring the
 * generation lock for the same program -- a property a mock can't demonstrate.
 */
@SpringBootTest
class ProgramGenerationLockServiceTest {

    @Autowired
    private ProgramGenerationLockService lockService;
    @Autowired
    private ProgramRepository programRepository;

    @Test
    void onlyOneOfTwoConcurrentRequestsAcquiresTheLock() throws Exception {
        UUID programId = persistProgram().getId();

        CountDownLatch startLine = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futures = List.of(
                pool.submit(() -> { startLine.await(); return lockService.tryAcquire(programId); }),
                pool.submit(() -> { startLine.await(); return lockService.tryAcquire(programId); })
            );
            startLine.countDown();

            long acquiredCount = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(10, TimeUnit.SECONDS)) acquiredCount++;
            }

            assertThat(acquiredCount).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aSecondAcquireFailsUntilTheFirstIsReleased() {
        UUID programId = persistProgram().getId();

        assertThat(lockService.tryAcquire(programId)).isTrue();
        assertThat(lockService.tryAcquire(programId)).isFalse();

        lockService.release(programId);

        assertThat(lockService.tryAcquire(programId)).isTrue();
    }

    @Test
    void aStaleLockCanBeReacquiredWithoutBeingReleased() {
        Program program = persistProgram();
        // Simulate a request that crashed mid-generation and never reached its finally-block
        // release() -- the lock should self-expire rather than wedge the program forever.
        program.setGenerationStartedAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        programRepository.save(program);

        assertThat(lockService.tryAcquire(program.getId())).isTrue();
    }

    private Program persistProgram() {
        Program program = new Program();
        program.setUserId(UUID.randomUUID());
        program.setProgramName("Test Program");
        program.setCurrentVertical(new BigDecimal("24"));
        program.setTargetVertical(new BigDecimal("30"));
        program.setDaysPerWeek(3);
        program.setExperienceLevel("beginner");
        return programRepository.save(program);
    }
}
