package com.loadedvj.backend.service;

import com.loadedvj.backend.domain.UserLimit;
import com.loadedvj.backend.repository.ApiUsageDailyRepository;
import com.loadedvj.backend.repository.UserLimitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageLimitServiceTest {

    @Mock
    private UserLimitRepository userLimitRepository;
    @Mock
    private ApiUsageDailyRepository apiUsageDailyRepository;
    @InjectMocks
    private UsageLimitService usageLimitService;

    private final UUID userId = UUID.randomUUID();

    @Test
    void allowsCallsAtOrBelowTheDefaultLimitWhenUserHasNoOverride() {
        when(userLimitRepository.findById(userId)).thenReturn(Optional.empty());
        when(apiUsageDailyRepository.incrementAndGet(userId)).thenReturn(UsageLimitService.DEFAULT_DAILY_LIMIT);

        assertDoesNotThrow(() -> usageLimitService.enforceDailyLimit(userId));
    }

    @Test
    void rejectsCallsOverTheDefaultLimitWhenUserHasNoOverride() {
        when(userLimitRepository.findById(userId)).thenReturn(Optional.empty());
        when(apiUsageDailyRepository.incrementAndGet(userId)).thenReturn(UsageLimitService.DEFAULT_DAILY_LIMIT + 1);

        assertThatThrownBy(() -> usageLimitService.enforceDailyLimit(userId))
            .isInstanceOf(DailyLimitExceededException.class)
            .hasMessageContaining(String.valueOf(UsageLimitService.DEFAULT_DAILY_LIMIT));
    }

    @Test
    void usesTheUsersOverrideLimitInsteadOfTheDefaultWhenPresent() {
        when(userLimitRepository.findById(userId)).thenReturn(Optional.of(new UserLimit(userId, 10)));
        when(apiUsageDailyRepository.incrementAndGet(userId)).thenReturn(10);

        assertDoesNotThrow(() -> usageLimitService.enforceDailyLimit(userId));
    }

    @Test
    void rejectsCallsOverAnOverrideLimitEvenWhenBelowTheDefault() {
        when(userLimitRepository.findById(userId)).thenReturn(Optional.of(new UserLimit(userId, 1)));
        when(apiUsageDailyRepository.incrementAndGet(userId)).thenReturn(2);

        assertThatThrownBy(() -> usageLimitService.enforceDailyLimit(userId))
            .isInstanceOf(DailyLimitExceededException.class)
            .hasMessageContaining("1");
    }
}
