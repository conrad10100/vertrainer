package com.loadedvj.backend.mesocycle;

import java.util.List;

public final class MesocycleCalculator {

    public record Phase(String name, String shortName, String description) {}

    public record PhaseInfo(int cyclePosition, int cycleNumber, Phase phase, boolean isDeload) {}

    private static final List<Phase> PHASES = List.of(
        new Phase("Accumulation", "Base",
            "General strength & work capacity — higher volume, moderate loads, building the base."),
        new Phase("Intensification", "Strength",
            "Maximal strength — heavier loads, lower reps, building the force ceiling."),
        new Phase("Realization", "Power",
            "Power & reactive strength — lighter/faster loads, plyometrics, converting strength to jump height. " +
            "Favor trap bar jumps, depth jumps, and other reactive/explosive variations here to convert " +
            "strength into speed.")
    );

    private MesocycleCalculator() { }

    public static PhaseInfo getPhaseInfo(int weekNum) {
        int cyclePosition = ((weekNum - 1) % 4) + 1;
        int cycleNumber = ((weekNum - 1) / 4) + 1;
        Phase phase = PHASES.get((cycleNumber - 1) % 3);
        boolean isDeload = cyclePosition == 4;
        return new PhaseInfo(cyclePosition, cycleNumber, phase, isDeload);
    }
}
