package com.loadedvj.backend.repository;

import com.loadedvj.backend.domain.VerticalCheckin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VerticalCheckinRepository extends JpaRepository<VerticalCheckin, UUID> {
    List<VerticalCheckin> findByUserIdOrderByRecordedAtAsc(UUID userId);
}
