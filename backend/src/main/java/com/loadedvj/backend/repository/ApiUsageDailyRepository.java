package com.loadedvj.backend.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class ApiUsageDailyRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Atomically increments today's call count for the user and returns the new
     * total. A single upsert avoids the read-then-write race a select+update
     * pair would have under concurrent requests.
     */
    public int incrementAndGet(UUID userId) {
        Object result = entityManager.createNativeQuery("""
                insert into api_usage_daily (user_id, usage_date, call_count)
                values (:userId, current_date, 1)
                on conflict (user_id, usage_date)
                do update set call_count = api_usage_daily.call_count + 1
                returning call_count
                """)
            .setParameter("userId", userId)
            .getSingleResult();
        return ((Number) result).intValue();
    }
}
