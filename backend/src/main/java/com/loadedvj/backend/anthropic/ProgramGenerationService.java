package com.loadedvj.backend.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredTextBlock;
import com.loadedvj.backend.anthropic.GenerationModels.DayGen;
import com.loadedvj.backend.anthropic.GenerationModels.ExerciseGen;
import com.loadedvj.backend.anthropic.GenerationModels.NextWeekResult;
import com.loadedvj.backend.anthropic.GenerationModels.ProgramCreationResult;
import com.loadedvj.backend.anthropic.PromptTemplateService.PromptSet;
import com.loadedvj.backend.audit.AiCallAuditLog;
import com.loadedvj.backend.audit.AiCallAuditLogService;
import com.loadedvj.backend.domain.Program;
import com.loadedvj.backend.mesocycle.MesocycleCalculator;
import com.loadedvj.backend.mesocycle.MesocycleCalculator.Phase;
import com.loadedvj.backend.mesocycle.MesocycleCalculator.PhaseInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Service
public class ProgramGenerationService {

    private final AnthropicClient client;
    private final String model;
    private final AiCallAuditLogService auditLogService;
    private final PromptTemplateService promptTemplateService;

    public ProgramGenerationService(AnthropicClient client, @Value("${anthropic.model}") String model,
                                     AiCallAuditLogService auditLogService,
                                     PromptTemplateService promptTemplateService) {
        this.client = client;
        this.model = model;
        this.auditLogService = auditLogService;
        this.promptTemplateService = promptTemplateService;
    }

    public ProgramCreationResult createFirstWeek(UUID userId, Program program) {
        PhaseInfo info = MesocycleCalculator.getPhaseInfo(1);
        Phase phase = info.phase();

        PromptSet prompts = promptTemplateService.load(
            "coach_persona", "create_first_week_system", "create_first_week_user");

        String system = prompts.get("coach_persona")
            + prompts.get("create_first_week_system").formatted(phase.name(), phase.description());

        String user = prompts.get("create_first_week_user").formatted(
            program.getCurrentVertical(), program.getTargetVertical(), nullToNone(program.getHeight()),
            nullToNone(program.getBodyweight()), squatTargetDisplay(program), program.getDaysPerWeek(),
            program.getExperienceLevel(), blankToNone(program.getNotes()));

        StructuredMessageCreateParams<ProgramCreationResult> params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(8000L)
            .system(system)
            .outputConfig(ProgramCreationResult.class)
            .addUserMessage(user)
            .build();

        int expectedDayCount = program.getDaysPerWeek();
        return callAndAudit(userId, "CREATE_FIRST_WEEK", prompts.version(), system, user, params,
            r -> validateFirstWeek(r, expectedDayCount));
    }

    public NextWeekResult generateNextWeek(UUID userId, Program program, int nextWeekNumber, String logSummary,
                                            String dayNotesSummary, String checkinSummary,
                                            String adherenceSummary, BigDecimal bestSquatWeight) {
        PhaseInfo info = MesocycleCalculator.getPhaseInfo(nextWeekNumber);
        PhaseInfo prevInfo = MesocycleCalculator.getPhaseInfo(nextWeekNumber - 1);
        boolean phaseChanged = !info.phase().name().equals(prevInfo.phase().name());
        Phase phase = info.phase();

        PromptSet prompts = promptTemplateService.load(
            "coach_persona", "generate_next_week_system", "generate_next_week_user");

        String system = prompts.get("coach_persona") + prompts.get("generate_next_week_system").formatted(
            nextWeekNumber, info.cycleNumber(), phase.name(), phase.description(),
            info.isDeload()
                ? " -- this is a DELOAD week: reduce volume (fewer sets, or drop 1-2 top sets) while "
                  + "keeping intensity relatively high, to let the athlete absorb the block before the "
                  + "phase shifts."
                : "",
            phaseChanged
                ? (" This is a NEW PHASE starting -- shift exercise emphasis, rep ranges, and loading "
                  + "style to match \"" + phase.name() + "\" rather than just continuing the previous "
                  + "phase's pattern.")
                : " Continue progressing within the same phase.");

        String user = prompts.get("generate_next_week_user").formatted(
            nullToNone(program.getHeight()), nullToNone(program.getBodyweight()),
            program.getCurrentVertical(), program.getTargetVertical(), program.getExperienceLevel(),
            program.getDaysPerWeek(), squatTargetDisplay(program), nextWeekNumber - 1, logSummary,
            blankToNone(dayNotesSummary), blankToNone(checkinSummary), blankToNone(adherenceSummary),
            squatProgressNote(program, bestSquatWeight), nextWeekNumber);

        StructuredMessageCreateParams<NextWeekResult> params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(12000L)
            .system(system)
            .outputConfig(NextWeekResult.class)
            .addUserMessage(user)
            .build();

        int expectedDayCount = program.getDaysPerWeek();
        return callAndAudit(userId, "GENERATE_NEXT_WEEK", prompts.version(), system, user, params,
            r -> validateDays(r.days(), expectedDayCount));
    }

    public ExerciseGen swapExercise(UUID userId, Program program, String dayFocus, String dayLabel,
                                     int cycleNumber, String phaseName, String phaseDescription,
                                     ExerciseGen currentExercise, String requestText) {
        PromptSet prompts = promptTemplateService.load("coach_persona", "swap_exercise_system", "swap_exercise_user");

        String system = prompts.get("coach_persona") + prompts.get("swap_exercise_system");

        String user = prompts.get("swap_exercise_user").formatted(
            dayFocus, dayLabel, cycleNumber, phaseName, phaseDescription,
            nullToNone(program.getHeight()), nullToNone(program.getBodyweight()),
            program.getCurrentVertical(), program.getTargetVertical(), program.getExperienceLevel(),
            currentExercise.name(), currentExercise.sets(), currentExercise.reps(),
            currentExercise.targetWeight(),
            currentExercise.notes() == null || currentExercise.notes().isBlank()
                ? "" : " -- note: " + currentExercise.notes(),
            requestText);

        StructuredMessageCreateParams<ExerciseGen> params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(2000L)
            .system(system)
            .outputConfig(ExerciseGen.class)
            .addUserMessage(user)
            .build();

        return callAndAudit(userId, "SWAP_EXERCISE", prompts.version(), system, user, params, r -> null);
    }

    /**
     * Makes the actual Claude call, times it, extracts the raw + parsed output and token usage,
     * runs the result through the given eval rule, and records one audit-log row for the attempt
     * -- whether it passed or failed. The audit write is fire-and-forget (AiCallAuditLogService is
     * @Async); this method never waits on it and its outcome never affects the caller.
     *
     * @param validator returns null if the result passes the eval rule, or a human-readable
     *                   failure reason if it doesn't.
     */
    private <T> T callAndAudit(UUID userId, String operation, String promptVersion, String systemPrompt,
                                String userPrompt, StructuredMessageCreateParams<T> params,
                                Function<T, String> validator) {
        long startNanos = System.nanoTime();
        StructuredMessage<T> response = client.messages().create(params);
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

        AiCallAuditLog auditEntry = new AiCallAuditLog();
        auditEntry.setUserId(userId);
        auditEntry.setOperation(operation);
        auditEntry.setPromptVersion(promptVersion);
        auditEntry.setModel(model);
        auditEntry.setSystemPrompt(systemPrompt);
        auditEntry.setUserPrompt(userPrompt);
        auditEntry.setLatencyMs(latencyMs);
        auditEntry.setInputTokens(response.usage().inputTokens());
        auditEntry.setOutputTokens(response.usage().outputTokens());

        Optional<StructuredTextBlock<T>> block = response.content().stream()
            .flatMap(b -> b.text().stream())
            .findFirst();

        if (block.isEmpty()) {
            auditEntry.setPassed(false);
            auditEntry.setFailureReason("Claude returned no structured content");
            auditLogService.record(auditEntry);
            throw new GenerationFailedException("Claude returned no structured content for " + operation);
        }

        auditEntry.setRawOutput(block.get().rawTextBlock().text());

        T result = block.get().text();
        String failureReason = validator.apply(result);
        auditEntry.setPassed(failureReason == null);
        auditEntry.setFailureReason(failureReason);
        auditLogService.record(auditEntry);

        if (failureReason != null) {
            throw new GenerationFailedException(failureReason);
        }
        return result;
    }

    /**
     * The eval rules a generated week must pass before it's allowed to reach the database:
     * exactly the requested number of days, and no day left with zero exercises.
     */
    private static String validateDays(List<DayGen> days, int expectedDayCount) {
        if (days.size() != expectedDayCount) {
            return "Claude returned " + days.size() + " day(s), expected " + expectedDayCount;
        }
        for (DayGen day : days) {
            if (day.exercises().isEmpty()) {
                return "Claude returned a day with no exercises: " + day.dayLabel();
            }
        }
        return null;
    }

    private static String validateFirstWeek(ProgramCreationResult result, int expectedDayCount) {
        if (result.programName() == null || result.programName().isBlank()) {
            return "Claude returned an empty program name";
        }
        return validateDays(result.days(), expectedDayCount);
    }

    private static String squatTargetDisplay(Program program) {
        if (program.getBodyweight() == null) {
            return "unknown (bodyweight not provided)";
        }
        BigDecimal target = program.getBodyweight().multiply(BigDecimal.valueOf(2));
        return "~" + target.stripTrailingZeros().toPlainString() + " lb (2x bodyweight)";
    }

    /**
     * Describes where the athlete stands on the 2x-bodyweight squat-strength goal so the model can
     * decide whether back squat should still be the primary bilateral squat-pattern lift, or whether
     * it's time to shift toward front squat / box squat / half-squat variations.
     */
    private static String squatProgressNote(Program program, BigDecimal bestSquatWeight) {
        if (bestSquatWeight == null) {
            return "No squat weight logged yet -- back squat should remain the primary bilateral "
                + "squat-pattern lift.";
        }
        if (program.getBodyweight() == null) {
            return "Best logged squat: " + bestSquatWeight.stripTrailingZeros().toPlainString()
                + " lb (bodyweight not provided, so goal progress is unknown -- keep back squat as the "
                + "primary bilateral squat-pattern lift).";
        }
        BigDecimal goal = program.getBodyweight().multiply(BigDecimal.valueOf(2));
        int pct = goal.signum() > 0
            ? (int) Math.round(bestSquatWeight.doubleValue() / goal.doubleValue() * 100)
            : 0;
        if (bestSquatWeight.compareTo(goal) >= 0) {
            return "Best logged squat: " + bestSquatWeight.stripTrailingZeros().toPlainString() + " lb, at "
                + "or above the ~" + goal.stripTrailingZeros().toPlainString() + " lb 2x-bodyweight goal ("
                + pct + "%) -- the squat-strength goal has been met. Shift primary bilateral-squat emphasis "
                + "away from back squat toward front squat, box squat, and half/partial squat variations.";
        }
        return "Best logged squat: " + bestSquatWeight.stripTrailingZeros().toPlainString() + " lb, " + pct
            + "% of the ~" + goal.stripTrailingZeros().toPlainString() + " lb 2x-bodyweight goal -- keep back "
            + "squat as the primary bilateral squat-pattern lift until that goal is reached.";
    }

    private static String nullToNone(Object value) {
        return value == null ? "not provided" : value.toString();
    }

    private static String blankToNone(String value) {
        return (value == null || value.isBlank()) ? "None provided" : value;
    }
}
