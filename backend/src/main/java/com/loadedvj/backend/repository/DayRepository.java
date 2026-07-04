package com.loadedvj.backend.repository;

import com.loadedvj.backend.domain.Day;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DayRepository extends JpaRepository<Day, UUID> {
}
