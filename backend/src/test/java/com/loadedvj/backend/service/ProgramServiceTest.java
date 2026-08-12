package com.loadedvj.backend.service;

import com.loadedvj.backend.domain.Day;
import com.loadedvj.backend.domain.Exercise;
import com.loadedvj.backend.domain.Program;
import com.loadedvj.backend.domain.Week;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProgramServiceTest {

    private final ProgramService programService = new ProgramService(null, null, null, null, null, null, null);

    @Test
    void returnsNullWhenNoSquatHasEverBeenLogged() {
        Program program = new Program();
        program.addWeek(weekWithExercise("Back Squat", null));

        assertThat(programService.findBestSquatWeight(program)).isNull();
    }

    @Test
    void returnsTheLoggedWeightForABilateralSquat() {
        Program program = new Program();
        program.addWeek(weekWithExercise("Back Squat", new BigDecimal("225")));

        assertThat(programService.findBestSquatWeight(program)).isEqualByComparingTo("225");
    }

    @Test
    void returnsTheHeaviestBilateralSquatAcrossMultipleWeeks() {
        Program program = new Program();
        program.addWeek(weekWithExercise("Front Squat", new BigDecimal("185")));
        program.addWeek(weekWithExercise("Box Squat", new BigDecimal("245")));

        assertThat(programService.findBestSquatWeight(program)).isEqualByComparingTo("245");
    }

    @Test
    void ignoresUnilateralSquatVariationsEvenWhenHeavier() {
        Program program = new Program();
        program.addWeek(weekWithExercise("Back Squat", new BigDecimal("225")));
        program.addWeek(weekWithExercise("Bulgarian Split Squat", new BigDecimal("999")));
        program.addWeek(weekWithExercise("Single Leg Squat", new BigDecimal("999")));

        assertThat(programService.findBestSquatWeight(program)).isEqualByComparingTo("225");
    }

    private Week weekWithExercise(String exerciseName, BigDecimal loggedWeight) {
        Week week = new Week();
        Day day = new Day();
        Exercise exercise = new Exercise();
        exercise.setName(exerciseName);
        exercise.setLoggedWeight(loggedWeight);
        day.addExercise(exercise);
        week.addDay(day);
        return week;
    }
}
