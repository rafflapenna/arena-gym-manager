package com.gymmanager.service;

import com.gymmanager.entity.SubscriptionStatus;
import com.gymmanager.exception.ResourceNotFoundException;
import com.gymmanager.repository.SubscriptionRepository;
import com.gymmanager.repository.SubscriptionStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Nightly job that transitions overdue ACTIVE subscriptions to EXPIRED.
 *
 * <p>Cron expression is configurable via {@code app.subscription.expiry-cron}
 * (default: every day at 01:00 server time).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpiryJob {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionStatusRepository subscriptionStatusRepository;

    @Scheduled(cron = "${app.subscription.expiry-cron:0 0 1 * * *}")
    @Transactional
    public void expireOverdueSubscriptions() {
        SubscriptionStatus expiredStatus = subscriptionStatusRepository.findByCode("EXPIRED")
                .orElseThrow(() -> new ResourceNotFoundException("Status EXPIRED not found"));

        int count = subscriptionRepository.expireOverdueSubscriptions(LocalDate.now(), expiredStatus);
        if (count > 0) {
            log.info("SubscriptionExpiryJob: expired {} subscription(s)", count);
        }
    }
}
