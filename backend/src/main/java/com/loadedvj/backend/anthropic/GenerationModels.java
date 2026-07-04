package com.loadedvj.backend.anthropic;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Shapes returned by Claude via structured outputs (output_config.format) —
 * these ARE the JSON schema (derived automatically from the record), so the
 * model's response deserializes straight into them with no markdown-fence
 * stripping or manual parsing.
 */
public final class GenerationModels {

    private GenerationModels() { }

    public record ExerciseGen(
        @JsonPropertyDescription("Exercise name") String name,
        @JsonPropertyDescription("Number of sets") int sets,
        @JsonPropertyDescription("Reps or a rep range as a string, e.g. \"5\" or \"3-5\"") String reps,
        @JsonPropertyDescription("""
            For any barbell, dumbbell, machine, or other externally-loaded exercise, this MUST be a \
            specific numeric load with unit (e.g. "225 lb" or "102 kg") -- never RPE, never a percentage, \
            never a vague description. Only use "Bodyweight" for true bodyweight movements (e.g. depth \
            jumps, split squats with no added load) -- for those, box/drop height in inches should be \
            calibrated to the athlete's current vertical ability. RPE or a percentage can appear in the \
            notes field as coaching context, but never here.""")
        String targetWeight,
        @JsonPropertyDescription("Short coaching cue, or an empty string") String notes
    ) { }

    public record DayGen(
        @JsonPropertyDescription("e.g. \"Day 1\"") String dayLabel,
        @JsonPropertyDescription("e.g. \"Lower Body Power\"") String focus,
        @JsonPropertyDescription("4-6 exercises for this day") List<ExerciseGen> exercises
    ) { }

    public record ProgramCreationResult(
        @JsonPropertyDescription("A short, motivating name for this training program") String programName,
        @JsonPropertyDescription("Week 1's training days, in order") List<DayGen> days
    ) { }

    public record NextWeekResult(
        @JsonPropertyDescription("This week's training days, in order") List<DayGen> days
    ) { }
}
