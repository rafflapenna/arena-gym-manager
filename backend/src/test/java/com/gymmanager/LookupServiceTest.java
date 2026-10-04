package com.gymmanager;

import com.gymmanager.dto.LookupDTO;
import com.gymmanager.dto.SubscriptionTypeRequestDTO;
import com.gymmanager.entity.Gym;
import com.gymmanager.exception.ConflictException;
import com.gymmanager.exception.ResourceNotFoundException;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.SubscriptionTypeRepository;
import com.gymmanager.service.LookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service-level tests for the per-gym subscription type list-price preset (roadmap: wizard
 * "Iscrizione & Incasso", DESIGN-wizard-iscrizione.it.md §3, and the later per-gym listino
 * migration): {@code defaultPrice} on create/read, {@code PUT /subscription-types/{id}}
 * (label/months/price update, code immutability, 404), and gym scoping (same code allowed across
 * gyms, cross-gym update looks like 404, listing is scoped to the caller's gym).
 *
 * <p>Subscription types are per-gym since the per-gym listino migration; each test creates its
 * own {@link Gym} (and unique-coded types within it) so tests stay independent of each other and
 * of the gym seeded by DataInitializer.
 */
@SpringBootTest
@Testcontainers
class LookupServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired LookupService lookupService;
    @Autowired SubscriptionTypeRepository subscriptionTypeRepository;
    @Autowired GymRepository gymRepository;

    private Long gymId;

    @BeforeEach
    void setUp() {
        gymId = gymRepository.save(Gym.builder().name("Lookup Test Gym " + UUID.randomUUID()).build()).getId();
    }

    private SubscriptionTypeRequestDTO request(String code, String label, int months, BigDecimal defaultPrice) {
        SubscriptionTypeRequestDTO dto = new SubscriptionTypeRequestDTO();
        dto.setCode(code);
        dto.setLabel(label);
        dto.setMonths(months);
        dto.setDefaultPrice(defaultPrice);
        return dto;
    }

    private String uniqueCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void create_withDefaultPrice_isPersistedAndReturned() {
        String code = uniqueCode("PRESET");
        LookupDTO created = lookupService.createSubscriptionType(
                request(code, "Preset Mensile", 1, new BigDecimal("50.00")), gymId);

        assertThat(created.getDefaultPrice()).isEqualByComparingTo("50.00");
        assertThat(created.getCode()).isEqualTo(code.toUpperCase());
    }

    @Test
    void create_withoutDefaultPrice_staysNull() {
        String code = uniqueCode("NOPRICE");
        LookupDTO created = lookupService.createSubscriptionType(request(code, "Senza listino", 1, null), gymId);

        assertThat(created.getDefaultPrice()).isNull();
    }

    @Test
    void getSubscriptionTypes_exposesDefaultPriceOfCreatedType() {
        String code = uniqueCode("LISTED");
        lookupService.createSubscriptionType(request(code, "Annuale Promo", 12, new BigDecimal("400.00")), gymId);

        List<LookupDTO> types = lookupService.getSubscriptionTypes(gymId);
        LookupDTO found = types.stream().filter(t -> t.getCode().equals(code.toUpperCase())).findFirst().orElseThrow();
        assertThat(found.getDefaultPrice()).isEqualByComparingTo("400.00");
        assertThat(found.getMonths()).isEqualTo(12);
    }

    @Test
    void update_changesLabelMonthsAndDefaultPrice() {
        String code = uniqueCode("UPD");
        Long id = lookupService.createSubscriptionType(request(code, "Old Label", 1, new BigDecimal("30.00")), gymId).getId();

        LookupDTO updated = lookupService.updateSubscriptionType(id,
                request(code, "New Label", 3, new BigDecimal("80.00")), gymId);

        assertThat(updated.getLabel()).isEqualTo("New Label");
        assertThat(updated.getMonths()).isEqualTo(3);
        assertThat(updated.getDefaultPrice()).isEqualByComparingTo("80.00");

        // Persisted, not just returned.
        List<LookupDTO> reloaded = lookupService.getSubscriptionTypes(gymId);
        LookupDTO persisted = reloaded.stream().filter(t -> t.getId().equals(id)).findFirst().orElseThrow();
        assertThat(persisted.getLabel()).isEqualTo("New Label");
        assertThat(persisted.getDefaultPrice()).isEqualByComparingTo("80.00");
    }

    @Test
    void update_canClearDefaultPriceBackToNull() {
        String code = uniqueCode("CLEAR");
        Long id = lookupService.createSubscriptionType(request(code, "Has Price", 1, new BigDecimal("20.00")), gymId).getId();

        LookupDTO updated = lookupService.updateSubscriptionType(id, request(code, "Has Price", 1, null), gymId);

        assertThat(updated.getDefaultPrice()).isNull();
    }

    @Test
    void update_codeInDtoIsIgnored_existingCodeIsImmutable() {
        String originalCode = uniqueCode("KEEP");
        Long id = lookupService.createSubscriptionType(request(originalCode, "Keep Me", 1, null), gymId).getId();

        LookupDTO updated = lookupService.updateSubscriptionType(id,
                request("DIFFERENT-CODE", "Keep Me Renamed", 1, new BigDecimal("10.00")), gymId);

        assertThat(updated.getCode()).isEqualTo(originalCode.toUpperCase());
        assertThat(subscriptionTypeRepository.findById(id).orElseThrow().getCode()).isEqualTo(originalCode.toUpperCase());
    }

    @Test
    void update_nonExistentId_throwsResourceNotFound() {
        assertThatThrownBy(() -> lookupService.updateSubscriptionType(Long.MAX_VALUE,
                request(uniqueCode("GHOST"), "Ghost", 1, null), gymId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_duplicateCode_returnsConflict() {
        String code = uniqueCode("DUP");
        lookupService.createSubscriptionType(request(code, "First", 1, null), gymId);

        assertThatThrownBy(() -> lookupService.createSubscriptionType(request(code, "Second", 2, null), gymId))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_sameCodeInDifferentGyms_bothSucceed() {
        Long otherGymId = gymRepository.save(Gym.builder().name("Other Gym " + UUID.randomUUID()).build()).getId();
        String code = uniqueCode("MULTI");

        LookupDTO first = lookupService.createSubscriptionType(request(code, "Mine", 1, null), gymId);
        LookupDTO second = lookupService.createSubscriptionType(request(code, "Theirs", 1, null), otherGymId);

        assertThat(first.getCode()).isEqualTo(second.getCode());
        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void update_typeOfAnotherGym_returns404() {
        Long otherGymId = gymRepository.save(Gym.builder().name("Other Gym " + UUID.randomUUID()).build()).getId();
        Long id = lookupService.createSubscriptionType(request(uniqueCode("XGYM"), "Foreign", 1, null), otherGymId).getId();

        assertThatThrownBy(() -> lookupService.updateSubscriptionType(id,
                request(uniqueCode("XGYM"), "Hijacked", 2, null), gymId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void getSubscriptionTypes_doesNotLeakTypesOfAnotherGym() {
        Long otherGymId = gymRepository.save(Gym.builder().name("Other Gym " + UUID.randomUUID()).build()).getId();
        String otherCode = uniqueCode("LEAK");
        lookupService.createSubscriptionType(request(otherCode, "Not Mine", 1, null), otherGymId);

        List<LookupDTO> mine = lookupService.getSubscriptionTypes(gymId);
        assertThat(mine).noneMatch(t -> t.getCode().equals(otherCode.toUpperCase()));
    }
}
