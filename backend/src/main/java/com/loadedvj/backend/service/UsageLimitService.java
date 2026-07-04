package com.loadedvj.backend.service;

import com.loadedvj.backend.domain.UserLimit;
import com.loadedvj.backend.repository.ApiUsageDailyRepository;
import com.loadedvj.backend.repository.UserLimitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UsageLimitService {

    public static final int DEFAULT_DAILY_LIMIT = 5;

    private final UserLimitRepository userLimitRepository;
    private final ApiUsageDailyRepository apiUsageDailyRepository;

    public UsageLimitService(UserLimitRepository userLimitRepository,
                              ApiUsageDailyRepository apiUsageDailyRepository) {
        this.userLimitRepository = userLimitRepository;
        this.apiUsageDailyRepository = apiUsageDailyRepository;
    }

    /**
     * Call before every paid (Anthropic-backed) operation. Throws once the
     * user's calls today exceed their limit (user_limits override, or
     * DEFAULT_DAILY_LIMIT if they have no override row).
     *
     * Runs in its own transaction so the increment commits immediately and
     * survives a rollback of the caller's transaction (e.g. ProgramService's,
     * which rolls back if the Anthropic call that follows fails) -- otherwise
     * a failed downstream call would silently "refund" the attempt.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enforceDailyLimit(UUID userId) {
        int limit = userLimitRepository.findById(userId)
            .map(UserLimit::getDailyCallLimit)
            .orElse(DEFAULT_DAILY_LIMIT);

        int used = apiUsageDailyRepository.incrementAndGet(userId);
        if (used > limit) {
            throw new DailyLimitExceededException(
                "Daily limit of " + limit + " AI generations reached. Try again tomorrow.");
        }
    }
}
