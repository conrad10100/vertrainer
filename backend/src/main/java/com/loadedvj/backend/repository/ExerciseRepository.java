package com.loadedvj.backend.repository;

import com.loadedvj.backend.domain.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ExerciseRepository extends JpaRepository<Exercise, UUID> {

    @Query("""
        select distinct e.name from Exercise e
        where e.day.week.program.userId = :userId
        order by e.name
        """)
    List<String> findDistinctExerciseNamesForUser(@Param("userId") UUID userId);

    @Query("""
        select e from Exercise e
        where e.day.week.program.userId = :userId and e.name = :name
        order by e.day.week.weekNumber asc
        """)
    List<Exercise> findByUserIdAndName(@Param("userId") UUID userId, @Param("name") String name);

    @Query("""
        select e from Exercise e
        where e.day.week.program.userId = :userId
        order by e.day.week.weekNumber asc
        """)
    List<Exercise> findAllForUser(@Param("userId") UUID userId);
}
