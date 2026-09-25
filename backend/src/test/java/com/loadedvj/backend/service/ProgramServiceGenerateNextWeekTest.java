package com.loadedvj.backend.service;

import com.loadedvj.backend.anthropic.ProgramGenerationService;
import com.loadedvj.backend.domain.Program;
import com.loadedvj.backend.domain.Week;
import com.loadedvj.backend.dto.ProgramDtos.WeekResponse;
import com.loadedvj.backend.repository.DayRepository;
import com.loadedvj.backend.repository.ExerciseRepository;
import com.loadedvj.backend.repository.ProgramRepository;
import com.loadedvj.backend.repository.VerticalCheckinRepository;
import com.loadedvj.backend.repository.WeekRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the race-safety additions to generateNextWeek(): a concurrent request that can't get the
 * generation lock is rejected instead of racing the insert, and a retry that finds the week
 * already committed gets it back instead of re-running Claude generation and re-spending quota.
 */
@ExtendWith(MockitoExtension.class)
class ProgramServiceGenerateNextWeekTest {

    @Mock private ProgramRepository programRepository;
    @Mock private WeekRepository weekRepository;
    @Mock private DayRepository dayRepository;
    @Mock private ExerciseRepository exerciseRepository;
    @Mock private VerticalCheckinRepository checkinRepository;
    @Mock private ProgramGenerationService generationService;
    @Mock private UsageLimitService usageLimitService;
    @Mock private ProgramGenerationLockService generationLockService;

    private final UUID userId = UUID.randomUUID();
    private final UUID programId = UUID.randomUUID();

    private ProgramService newService() {
        return new ProgramService(programRepository, weekRepository, dayRepository, exerciseRepository,
            checkinRepository, generationService, usageLimitService, generationLockService);
    }

    @Test
    void rejectsARequestThatCannotAcquireTheGenerationLock() {
        Program program = programOwnedBy(userId);
        Week lastWeek = weekNumbered(5, Instant.now().minus(21, ChronoUnit.HOURS));

        when(programRepository.findById(programId)).thenReturn(Optional.of(program));
        when(weekRepository.findTopByProgramIdOrderByWeekNumberDesc(programId)).thenReturn(Optional.of(lastWeek));
        when(generationLockService.tryAcquire(programId)).thenReturn(false);

        ProgramService service = newService();

        assertThatThrownBy(() -> service.generateNextWeek(userId, programId))
            .isInstanceOf(GenerationInProgressException.class);

        verify(generationLockService, never()).release(any());
        verifyNoInteractions(usageLimitService, generationService);
    }

    @Test
    void returnsTheAlreadyGeneratedWeekInsteadOfCallingClaudeAgain() {
        Program program = programOwnedBy(userId);
        Week lastWeek = weekNumbered(5, Instant.now().minus(21, ChronoUnit.HOURS));
        Week alreadyGenerated = weekNumbered(6, Instant.now());

        when(programRepository.findById(programId)).thenReturn(Optional.of(program));
        when(weekRepository.findTopByProgramIdOrderByWeekNumberDesc(programId)).thenReturn(Optional.of(lastWeek));
        when(generationLockService.tryAcquire(programId)).thenReturn(true);
        when(weekRepository.findByProgramIdAndWeekNumber(programId, 6)).thenReturn(Optional.of(alreadyGenerated));

        ProgramService service = newService();

        WeekResponse response = service.generateNextWeek(userId, programId);

        assertThat(response.weekNumber()).isEqualTo(6);
        verify(generationLockService, times(1)).release(programId);
        verifyNoInteractions(usageLimitService, generationService);
    }

    private Program programOwnedBy(UUID owner) {
        Program program = new Program();
        program.setUserId(owner);
        program.setProgramName("Test Program");
        program.setCurrentVertical(new BigDecimal("24"));
        program.setTargetVertical(new BigDecimal("30"));
        program.setBodyweight(new BigDecimal("180"));
        program.setDaysPerWeek(3);
        program.setExperienceLevel("beginner");
        return program;
    }

    private Week weekNumbered(int weekNumber, Instant createdAt) {
        Week week = new Week();
        week.setWeekNumber(weekNumber);
        week.setCyclePosition(1);
        week.setCycleNumber(1);
        week.setPhase("BASE");
        week.setDeload(false);
        try {
            var field = Week.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(week, createdAt);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return week;
    }
}
