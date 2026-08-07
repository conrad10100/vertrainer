package com.loadedvj.backend.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiCallAuditLogRepository extends JpaRepository<AiCallAuditLog, UUID> {
}
