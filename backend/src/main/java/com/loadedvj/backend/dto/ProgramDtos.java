package com.loadedvj.backend.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProgramDtos {

    private ProgramDtos() { }

    public record CreateProgramRequest(
        @NotNull BigDecimal currentVertical,
        @NotNull BigDecimal targetVertical,
        BigDecimal height,
        BigDecimal bodyweight,
        @Min(1) @Max(7) int daysPerWeek,
        @NotBlank String experienceLevel,
        String notes
    ) { }

    public record ExerciseResponse(
        UUID id, String name, int sets, String reps, String targetWeight, String notes,
        BigDecimal loggedWeight, Integer loggedReps
    ) { }

    public record DayResponse(
        UUID id, String dayLabel, String focus, String athleteNote, List<ExerciseResponse> exercises
    ) { }

    public record WeekResponse(
        UUID id, int weekNumber, int cyclePosition, int cycleNumber, String phase, boolean deload,
        List<DayResponse> days
    ) { }

    public record ProgramResponse(
        UUID id, String programName, BigDecimal currentVertical, BigDecimal targetVertical,
        BigDecimal height, BigDecimal bodyweight, int daysPerWeek, String experienceLevel, String notes,
        List<WeekResponse> weeks
    ) { }

    public record LogExerciseRequest(BigDecimal loggedWeight, Integer loggedReps) { }

    public record SwapExerciseRequest(@NotBlank String requestText) { }

    public record DayNoteRequest(String athleteNote) { }

    public record CheckinRequest(@NotNull BigDecimal inches, Instant recordedAt, String notes) { }

    public record CheckinResponse(UUID id, BigDecimal inches, Instant recordedAt, String notes) { }

    public record CheckinPoint(Instant recordedAt, BigDecimal inches) { }

    public record LiftPoint(int weekNumber, BigDecimal targetWeight) { }

    public record AdherencePoint(int weekNumber, double loggedFraction) { }

    public record DashboardResponse(
        List<CheckinPoint> verticalJumpHistory, List<LiftPoint> liftProgression, List<AdherencePoint> adherence
    ) { }
}
