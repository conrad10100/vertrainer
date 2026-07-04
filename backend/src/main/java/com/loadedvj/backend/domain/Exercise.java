package com.loadedvj.backend.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exercises")
public class Exercise {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_id", nullable = false)
    private Day day;

    @Column(name = "exercise_index", nullable = false)
    private int exerciseIndex;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int sets;

    @Column(nullable = false)
    private String reps;

    @Column(name = "target_weight")
    private String targetWeight;

    private String notes;

    @Column(name = "logged_weight")
    private BigDecimal loggedWeight;

    @Column(name = "logged_reps")
    private Integer loggedReps;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public Day getDay() { return day; }
    public void setDay(Day day) { this.day = day; }
    public int getExerciseIndex() { return exerciseIndex; }
    public void setExerciseIndex(int exerciseIndex) { this.exerciseIndex = exerciseIndex; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getSets() { return sets; }
    public void setSets(int sets) { this.sets = sets; }
    public String getReps() { return reps; }
    public void setReps(String reps) { this.reps = reps; }
    public String getTargetWeight() { return targetWeight; }
    public void setTargetWeight(String targetWeight) { this.targetWeight = targetWeight; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public BigDecimal getLoggedWeight() { return loggedWeight; }
    public void setLoggedWeight(BigDecimal loggedWeight) { this.loggedWeight = loggedWeight; }
    public Integer getLoggedReps() { return loggedReps; }
    public void setLoggedReps(Integer loggedReps) { this.loggedReps = loggedReps; }
    public Instant getCreatedAt() { return createdAt; }
}
