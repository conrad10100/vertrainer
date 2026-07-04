package com.loadedvj.backend.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.loadedvj.backend.anthropic.GenerationModels.ExerciseGen;
import com.loadedvj.backend.anthropic.GenerationModels.NextWeekResult;
import com.loadedvj.backend.anthropic.GenerationModels.ProgramCreationResult;
import com.loadedvj.backend.domain.Program;
import com.loadedvj.backend.mesocycle.MesocycleCalculator;
import com.loadedvj.backend.mesocycle.MesocycleCalculator.Phase;
import com.loadedvj.backend.mesocycle.MesocycleCalculator.PhaseInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ProgramGenerationService {

    private static final String COACH_PERSONA =
        "You are a strength & conditioning coach specializing in vertical jump development.";

    private final AnthropicClient client;
    private final String model;

    public ProgramGenerationService(AnthropicClient client, @Value("${anthropic.model}") String model) {
        this.client = client;
        this.model = model;
    }

    public ProgramCreationResult createFirstWeek(Program program) {
        PhaseInfo info = MesocycleCalculator.getPhaseInfo(1);
        Phase phase = info.phase();

        String system = COACH_PERSONA + """

            Build exactly ONE week (week 1) with exactly the requested number of training days, \
            structured for vertical jump development (lower body strength, hip/posterior chain, \
            plyometric and reactive elements as appropriate for a week-1 base). This is week 1 of \
            Cycle 1, phase "%s": %s
            Use the athlete's height, bodyweight, and current vertical jump to estimate sensible, \
            realistic starting loads for their main lifts (relative-strength based on bodyweight and \
            experience level) -- don't invent numbers disconnected from their profile. Each day should \
            have 4-6 exercises. Order days logically for recovery (don't stack the same movement \
            patterns back-to-back if days per week is high).
            """.formatted(phase.name(), phase.description());

        String user = """
            Athlete profile:
            - Current vertical: %s in
            - Target vertical: %s in
            - Height: %s in
            - Bodyweight: %s lb
            - Days per week: %d
            - Experience level: %s
            - Additional context: %s

            Build week 1 of my vertical jump program.
            """.formatted(
                program.getCurrentVertical(), program.getTargetVertical(), nullToNone(program.getHeight()),
                nullToNone(program.getBodyweight()), program.getDaysPerWeek(), program.getExperienceLevel(),
                blankToNone(program.getNotes()));

        StructuredMessageCreateParams<ProgramCreationResult> params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(8000L)
            .system(system)
            .outputConfig(ProgramCreationResult.class)
            .addUserMessage(user)
            .build();

        return client.messages().create(params).content().stream()
            .flatMap(block -> block.text().stream())
            .map(text -> text.text())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Claude returned no structured content for week 1"));
    }

    public NextWeekResult generateNextWeek(Program program, int nextWeekNumber, String logSummary,
                                            String dayNotesSummary, String checkinSummary,
                                            String adherenceSummary) {
        PhaseInfo info = MesocycleCalculator.getPhaseInfo(nextWeekNumber);
        PhaseInfo prevInfo = MesocycleCalculator.getPhaseInfo(nextWeekNumber - 1);
        boolean phaseChanged = !info.phase().name().equals(prevInfo.phase().name());
        Phase phase = info.phase();

        String system = COACH_PERSONA + """
             The athlete just finished a training week. Below is what was prescribed versus what they \
            actually logged, plus any context the athlete added for specific days.

            This next week is week %d: Cycle %d, phase "%s" (%s)%s%s

            Apply progressive overload using the log: if they hit or exceeded prescribed reps at the \
            prescribed weight, increase weight appropriately for the current phase's rep range and their \
            experience level; if they missed reps notably, hold the weight or reduce it slightly; if an \
            exercise wasn't logged, keep it the same or apply a small standard progression. You may swap \
            in phase-appropriate exercises (e.g. moving from squats/RDLs toward jump squats, trap bar \
            jumps, or depth jumps as phases shift toward power/reactive work), but keep continuity where \
            it makes sense for tracking. If the athlete added day-specific context (travel, no equipment \
            access, an injury, etc.), adapt that day's exercises and loading accordingly -- do not ignore it.

            Also weigh the athlete's longer-term trend, not just this single week:
            - Vertical jump check-in history: if measurements have stalled or regressed across multiple \
            check-ins despite good adherence, don't just continue the same progression -- make a more \
            assertive change (new exercise variations, a bigger shift toward reactive/power work, or an \
            extra deload) since the current approach isn't producing results. If check-ins show steady \
            improvement, the current approach is working -- continue it.
            - Adherence history: if adherence has been consistently low across recent weeks, hold or reduce \
            volume rather than progressing it further, and use the day notes to figure out what's getting \
            in the way rather than assuming the prescription itself was fine.
            """.formatted(
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

        String user = """
            Athlete profile: height %s in, bodyweight %s lb, current vertical %s in, target vertical %s in, \
            experience %s, %d days/week.

            Week %d results:

            %s

            Day-specific context the athlete added for the upcoming week:
            %s

            Vertical jump check-in history:
            %s

            Adherence by week so far this program:
            %s

            Build week %d.
            """.formatted(
                nullToNone(program.getHeight()), nullToNone(program.getBodyweight()),
                program.getCurrentVertical(), program.getTargetVertical(), program.getExperienceLevel(),
                program.getDaysPerWeek(), nextWeekNumber - 1, logSummary,
                blankToNone(dayNotesSummary), blankToNone(checkinSummary), blankToNone(adherenceSummary),
                nextWeekNumber);

        StructuredMessageCreateParams<NextWeekResult> params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(8000L)
            .system(system)
            .outputConfig(NextWeekResult.class)
            .addUserMessage(user)
            .build();

        return client.messages().create(params).content().stream()
            .flatMap(block -> block.text().stream())
            .map(text -> text.text())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "Claude returned no structured content for week " + nextWeekNumber));
    }

    public ExerciseGen swapExercise(Program program, String dayFocus, String dayLabel, int cycleNumber,
                                     String phaseName, String phaseDescription, ExerciseGen currentExercise,
                                     String requestText) {
        String system = COACH_PERSONA + """
             The athlete wants to customize one exercise in their program. Make the replacement fit the \
            day's training focus and the current periodization phase. Honor the athlete's request as \
            directly as possible (a specific swap, or a constraint like an injury to work around). If \
            their request is vague, use good coaching judgment for what would serve this day's focus.
            """;

        String user = """
            Day focus: %s (%s)
            Current phase: Cycle %d, %s -- %s
            Athlete profile: height %s in, bodyweight %s lb, current vertical %s in, target vertical %s in, \
            experience %s.

            Current exercise being replaced: %s (%d x %s @ %s%s)

            Athlete's request: "%s"

            Return the replacement exercise.
            """.formatted(
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

        return client.messages().create(params).content().stream()
            .flatMap(block -> block.text().stream())
            .map(text -> text.text())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Claude returned no replacement exercise"));
    }

    private static String nullToNone(Object value) {
        return value == null ? "not provided" : value.toString();
    }

    private static String blankToNone(String value) {
        return (value == null || value.isBlank()) ? "None provided" : value;
    }
}
