package com.loadedvj.backend.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "days")
public class Day {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "week_id", nullable = false)
    private Week week;

    @Column(name = "day_index", nullable = false)
    private int dayIndex;

    @Column(name = "day_label", nullable = false)
    private String dayLabel;

    @Column(nullable = false)
    private String focus;

    // Free-text context the athlete adds for this day (e.g. "on vacation next
    // week, no gym access"). Read when generating the FOLLOWING week.
    @Column(name = "athlete_note")
    private String athleteNote;

    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("exerciseIndex ASC")
    private List<Exercise> exercises = new ArrayList<>();

    public UUID getId() { return id; }
    public Week getWeek() { return week; }
    public void setWeek(Week week) { this.week = week; }
    public int getDayIndex() { return dayIndex; }
    public void setDayIndex(int dayIndex) { this.dayIndex = dayIndex; }
    public String getDayLabel() { return dayLabel; }
    public void setDayLabel(String dayLabel) { this.dayLabel = dayLabel; }
    public String getFocus() { return focus; }
    public void setFocus(String focus) { this.focus = focus; }
    public String getAthleteNote() { return athleteNote; }
    public void setAthleteNote(String athleteNote) { this.athleteNote = athleteNote; }
    public List<Exercise> getExercises() { return exercises; }

    public void addExercise(Exercise exercise) {
        exercises.add(exercise);
        exercise.setDay(this);
    }
}
