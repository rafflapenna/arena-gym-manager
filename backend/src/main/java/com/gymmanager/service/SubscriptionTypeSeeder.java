package com.gymmanager.service;

import com.gymmanager.entity.Gym;
import com.gymmanager.entity.SubscriptionType;
import com.gymmanager.repository.SubscriptionTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Seeds a gym's own copy of the subscription-type price list ("Listino"): {@code SubscriptionType}
 * moved from a single global lookup to a per-gym one (see schema.sql's per-gym listino migration),
 * and this is what makes that transition transparent both for gyms that already existed before the
 * migration and for a brand-new gym on day one.
 *
 * <p>{@link #ensureDefaultsForGym} is called (a) at startup, once per existing gym, by
 * {@code DataInitializer} — an idempotent migration for gyms that don't have their copies yet —
 * and (b) right after a new gym is created, by {@link GymService#create}, so it starts with a
 * base price list instead of an empty one.
 *
 * <p>Templates start from the 3 historical hardcoded defaults (MONTHLY/QUARTERLY/ANNUAL,
 * {@code defaultPrice} null) and are overlaid with the pre-migration legacy global rows
 * ({@code gym == null}, returned by {@link SubscriptionTypeRepository#findByGymIsNullOrderByIdAsc}):
 * where a legacy row's code matches one of the 3 defaults, the DB row wins (it carries whatever
 * label/months/defaultPrice prod already had); any legacy code outside the 3 defaults is added as
 * its own extra template. Each template is only copied into the gym if it doesn't already have
 * that code, so calling this repeatedly (every startup, or for a partially-seeded gym) is safe.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionTypeSeeder {

    private final SubscriptionTypeRepository subscriptionTypeRepository;

    private record Template(String code, String label, int months, BigDecimal defaultPrice) {
    }

    @Transactional
    public void ensureDefaultsForGym(Gym gym) {
        Map<String, Template> templates = new LinkedHashMap<>();
        templates.put("MONTHLY", new Template("MONTHLY", "Monthly", 1, null));
        templates.put("QUARTERLY", new Template("QUARTERLY", "Quarterly", 3, null));
        templates.put("ANNUAL", new Template("ANNUAL", "Annual", 12, null));

        for (SubscriptionType legacy : subscriptionTypeRepository.findByGymIsNullOrderByIdAsc()) {
            templates.put(legacy.getCode(), new Template(
                    legacy.getCode(), legacy.getLabel(), legacy.getMonths(), legacy.getDefaultPrice()));
        }

        for (Template t : templates.values()) {
            if (!subscriptionTypeRepository.existsByGymIdAndCode(gym.getId(), t.code())) {
                subscriptionTypeRepository.save(SubscriptionType.builder()
                        .code(t.code())
                        .label(t.label())
                        .months(t.months())
                        .defaultPrice(t.defaultPrice())
                        .gym(gym)
                        .build());
                log.info("Seeded subscription type {} for gym id={}", t.code(), gym.getId());
            }
        }
    }
}
