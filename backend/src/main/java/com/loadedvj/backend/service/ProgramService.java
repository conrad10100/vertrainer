package com.loadedvj.backend.service;

import com.loadedvj.backend.anthropic.GenerationModels.DayGen;
import com.loadedvj.backend.anthropic.GenerationModels.ExerciseGen;
import com.loadedvj.backend.anthropic.GenerationModels.NextWeekResult;
import com.loadedvj.backend.anthropic.GenerationModels.ProgramCreationResult;
import com.loadedvj.backend.anthropic.ProgramGenerationService;
import com.loadedvj.backend.domain.Day;
import com.loadedvj.backend.domain.Exercise;
import com.loadedvj.backend.domain.Program;
import com.loadedvj.backend.domain.Week;
import com.loadedvj.backend.dto.ProgramDtos.*;
import com.loadedvj.backend.mesocycle.MesocycleCalculator;
import com.loadedvj.backend.mesocycle.MesocycleCalculator.PhaseInfo;
import com.loadedvj.backend.repository.DayRepository;
import com.loadedvj.backend.repository.ExerciseRepository;
import com.loadedvj.backend.repository.ProgramRepository;
import com.loadedvj.backend.repository.WeekRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ProgramService {

    private final ProgramRepository programRepository;
    private final WeekRepository weekRepository;
    private final DayRepository dayRepository;
    private final ExerciseRepository exerciseRepository;
    private final ProgramGenerationService generationService;
    private final UsageLimitService usageLimitService;

    public ProgramService(ProgramRepository programRepository, WeekRepository weekRepository,
                           DayRepository dayRepository, ExerciseRepository exerciseRepository,
                           ProgramGenerationService generationService, UsageLimitService usageLimitService) {
        this.programRepository = programRepository;
        this.weekRepository = weekRepository;
        this.dayRepository = dayRepository;
        this.exerciseRepository = exerciseRepository;
        this.generationService = generationService;
        this.usageLimitService = usageLimitService;
    }

    public ProgramResponse createProgram(UUID userId, CreateProgramRequest req) {
        usageLimitService.enforceDailyLimit(userId);

        programRepository.findByUserIdAndActiveTrue(userId)
            .ifPresent(existing -> { existing.setActive(false); programRepository.save(existing); });

        Program program = new Program();
        program.setUserId(userId);
        program.setCurrentVertical(req.currentVertical());
        program.setTargetVertical(req.targetVertical());
        program.setHeight(req.height());
        program.setBodyweight(req.bodyweight());
        program.setDaysPerWeek(req.daysPerWeek());
        program.setExperienceLevel(req.experienceLevel());
        program.setNotes(req.notes());
        program.setActive(true);

        ProgramCreationResult result = generationService.createFirstWeek(program);
        program.setProgramName(result.programName());

        PhaseInfo info = MesocycleCalculator.getPhaseInfo(1);
        program.addWeek(buildWeek(1, info, result.days()));

        return toResponse(programRepository.save(program));
    }

    public ProgramResponse getActiveProgram(UUID userId) {
        Program program = programRepository.findByUserIdAndActiveTrue(userId)
            .orElseThrow(() -> new EntityNotFoundException("No active program"));
        return toResponse(program);
    }

    public WeekResponse generateNextWeek(UUID userId, UUID programId) {
        usageLimitService.enforceDailyLimit(userId);

        Program program = requireOwnedProgram(userId, programId);
        Week lastWeek = weekRepository.findTopByProgramIdOrderByWeekNumberDesc(programId)
            .orElseThrow(() -> new EntityNotFoundException("Program has no weeks yet"));

        int nextWeekNumber = lastWeek.getWeekNumber() + 1;
        String logSummary = buildLogSummary(lastWeek);
        String dayNotesSummary = buildDayNotesSummary(lastWeek);

        NextWeekResult result = generationService.generateNextWeek(program, nextWeekNumber, logSummary, dayNotesSummary);
        PhaseInfo info = MesocycleCalculator.getPhaseInfo(nextWeekNumber);
        Week week = buildWeek(nextWeekNumber, info, result.days());
        program.addWeek(week);
        programRepository.save(program);

        return toWeekResponse(week);
    }

    public ExerciseResponse logExercise(UUID userId, UUID exerciseId, LogExerciseRequest req) {
        Exercise exercise = requireOwnedExercise(userId, exerciseId);
        exercise.setLoggedWeight(req.loggedWeight());
        exercise.setLoggedReps(req.loggedReps());
        return toExerciseResponse(exerciseRepository.save(exercise));
    }

    public ExerciseResponse swapExercise(UUID userId, UUID exerciseId, SwapExerciseRequest req) {
        usageLimitService.enforceDailyLimit(userId);

        Exercise exercise = requireOwnedExercise(userId, exerciseId);
        Day day = exercise.getDay();
        Week week = day.getWeek();
        PhaseInfo info = MesocycleCalculator.getPhaseInfo(week.getWeekNumber());

        ExerciseGen current = new ExerciseGen(exercise.getName(), exercise.getSets(), exercise.getReps(),
            exercise.getTargetWeight(), exercise.getNotes());

        ExerciseGen replacement = generationService.swapExercise(week.getProgram(), day.getFocus(),
            day.getDayLabel(), info.cycleNumber(), info.phase().name(), info.phase().description(),
            current, req.requestText());

        exercise.setName(replacement.name());
        exercise.setSets(replacement.sets());
        exercise.setReps(replacement.reps());
        exercise.setTargetWeight(replacement.targetWeight());
        exercise.setNotes(replacement.notes());
        exercise.setLoggedWeight(null);
        exercise.setLoggedReps(null);

        return toExerciseResponse(exerciseRepository.save(exercise));
    }

    public void removeExercise(UUID userId, UUID exerciseId) {
        Exercise exercise = requireOwnedExercise(userId, exerciseId);
        exerciseRepository.delete(exercise);
    }

    public DayResponse updateDayNote(UUID userId, UUID dayId, DayNoteRequest req) {
        Day day = dayRepository.findById(dayId)
            .orElseThrow(() -> new EntityNotFoundException("Day not found"));
        if (!day.getWeek().getProgram().getUserId().equals(userId)) {
            throw new AccessDeniedException("Not your day");
        }
        day.setAthleteNote(req.athleteNote());
        return toDayResponse(dayRepository.save(day));
    }

    // ---- helpers ----

    private Program requireOwnedProgram(UUID userId, UUID programId) {
        Program program = programRepository.findById(programId)
            .orElseThrow(() -> new EntityNotFoundException("Program not found"));
        if (!program.getUserId().equals(userId)) {
            throw new AccessDeniedException("Not your program");
        }
        return program;
    }

    private Exercise requireOwnedExercise(UUID userId, UUID exerciseId) {
        Exercise exercise = exerciseRepository.findById(exerciseId)
            .orElseThrow(() -> new EntityNotFoundException("Exercise not found"));
        if (!exercise.getDay().getWeek().getProgram().getUserId().equals(userId)) {
            throw new AccessDeniedException("Not your exercise");
        }
        return exercise;
    }

    private Week buildWeek(int weekNumber, PhaseInfo info, List<DayGen> dayGens) {
        Week week = new Week();
        week.setWeekNumber(weekNumber);
        week.setCyclePosition(info.cyclePosition());
        week.setCycleNumber(info.cycleNumber());
        week.setPhase(info.phase().name());
        week.setDeload(info.isDeload());

        int dayIndex = 0;
        for (DayGen dayGen : dayGens) {
            Day day = new Day();
            day.setDayIndex(dayIndex++);
            day.setDayLabel(dayGen.dayLabel());
            day.setFocus(dayGen.focus());
            week.addDay(day);

            int exIndex = 0;
            for (ExerciseGen exGen : dayGen.exercises()) {
                Exercise exercise = new Exercise();
                exercise.setExerciseIndex(exIndex++);
                exercise.setName(exGen.name());
                exercise.setSets(exGen.sets());
                exercise.setReps(exGen.reps());
                exercise.setTargetWeight(exGen.targetWeight());
                exercise.setNotes(exGen.notes());
                day.addExercise(exercise);
            }
        }
        return week;
    }

    private String buildLogSummary(Week week) {
        StringBuilder sb = new StringBuilder();
        for (Day day : week.getDays()) {
            sb.append(day.getDayLabel()).append(" (").append(day.getFocus()).append("):\n");
            for (Exercise ex : day.getExercises()) {
                String did = ex.getLoggedWeight() != null ? ex.getLoggedWeight().toString() : "not logged";
                String reps = ex.getLoggedReps() != null ? ex.getLoggedReps().toString() : "not logged";
                sb.append("  - ").append(ex.getName()).append(": prescribed ").append(ex.getSets())
                    .append("x").append(ex.getReps()).append(" @ ").append(ex.getTargetWeight())
                    .append(" -> actually did: ").append(did).append(" for ").append(reps).append(" reps\n");
            }
        }
        return sb.toString();
    }

    private String buildDayNotesSummary(Week week) {
        StringBuilder sb = new StringBuilder();
        for (Day day : week.getDays()) {
            if (day.getAthleteNote() != null && !day.getAthleteNote().isBlank()) {
                sb.append(day.getDayLabel()).append(": ").append(day.getAthleteNote()).append("\n");
            }
        }
        return sb.isEmpty() ? "" : sb.toString();
    }

    private ProgramResponse toResponse(Program program) {
        return new ProgramResponse(program.getId(), program.getProgramName(), program.getCurrentVertical(),
            program.getTargetVertical(), program.getHeight(), program.getBodyweight(), program.getDaysPerWeek(),
            program.getExperienceLevel(), program.getNotes(),
            program.getWeeks().stream().map(this::toWeekResponse).toList());
    }

    private WeekResponse toWeekResponse(Week week) {
        return new WeekResponse(week.getId(), week.getWeekNumber(), week.getCyclePosition(),
            week.getCycleNumber(), week.getPhase(), week.isDeload(),
            week.getDays().stream().map(this::toDayResponse).toList());
    }

    private DayResponse toDayResponse(Day day) {
        return new DayResponse(day.getId(), day.getDayLabel(), day.getFocus(), day.getAthleteNote(),
            day.getExercises().stream().map(this::toExerciseResponse).toList());
    }

    private ExerciseResponse toExerciseResponse(Exercise exercise) {
        return new ExerciseResponse(exercise.getId(), exercise.getName(), exercise.getSets(), exercise.getReps(),
            exercise.getTargetWeight(), exercise.getNotes(), exercise.getLoggedWeight(), exercise.getLoggedReps());
    }
}
