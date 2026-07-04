package com.loadedvj.backend.service;

import com.loadedvj.backend.dto.AdminDtos.UserUsageResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminService {

    @PersistenceContext
    private EntityManager entityManager;

    private final Set<String> adminEmails;

    public AdminService(@Value("${app.admin.emails:}") String adminEmailsCsv) {
        this.adminEmails = Arrays.stream(adminEmailsCsv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
    }

    public void requireAdmin(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || !adminEmails.contains(email.toLowerCase())) {
            throw new AccessDeniedException("Admin access required");
        }
    }

    @SuppressWarnings("unchecked")
    public List<UserUsageResponse> getUsageSummary() {
        List<Object[]> rows = entityManager.createNativeQuery("""
                select au.email,
                       coalesce(ul.daily_call_limit, :defaultLimit) as daily_call_limit,
                       coalesce(uad.call_count, 0) as used_today
                from auth.users au
                left join public.user_limits ul on ul.user_id = au.id
                left join public.api_usage_daily uad
                    on uad.user_id = au.id and uad.usage_date = current_date
                order by au.email
                """)
            .setParameter("defaultLimit", UsageLimitService.DEFAULT_DAILY_LIMIT)
            .getResultList();

        return rows.stream()
            .map(row -> new UserUsageResponse((String) row[0], ((Number) row[1]).intValue(), ((Number) row[2]).intValue()))
            .toList();
    }
}
