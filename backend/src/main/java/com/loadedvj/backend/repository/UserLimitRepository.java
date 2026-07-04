package com.loadedvj.backend.repository;

import com.loadedvj.backend.domain.UserLimit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserLimitRepository extends JpaRepository<UserLimit, UUID> {
}
