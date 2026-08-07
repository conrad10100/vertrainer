package com.loadedvj.backend.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiCallAuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AiCallAuditLogService.class);

    private final AiCallAuditLogRepository repository;

    public AiCallAuditLogService(AiCallAuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Fire-and-forget: runs on a dedicated background thread pool (see AsyncConfig), outside the
     * caller's request/transaction. Callers must not wait on this and must not let its outcome
     * affect the generation flow -- a failure here is a monitoring gap, never a user-facing error.
     */
    @Async("auditLogExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AiCallAuditLog entry) {
        try {
            repository.save(entry);
        } catch (Exception e) {
            log.error("Failed to write AI call audit log for operation '{}'", entry.getOperation(), e);
        }
    }
}
