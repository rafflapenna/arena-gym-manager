package com.gymmanager.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Polymorphic: exactly one of {@code typeId} (classic "generic access") or
 * {@code packageId} (roadmap §4 A.4) must be provided — enforced in
 * {@code SubscriptionService.create} (422 otherwise), not via bean validation, since
 * cross-field XOR rules aren't expressible with simple annotations.
 *
 * <p>{@code price} is required (and used as-is) only for the {@code typeId} branch. For the
 * {@code packageId} branch it is ignored: the price is auto-populated from the package and
 * frozen as a snapshot ({@code Subscription.purchasePrice}) — sending it is harmless but has
 * no effect.
 */
@Data
public class SubscriptionRequestDTO {

    private Long typeId;

    private Long packageId;

    @NotNull
    private LocalDate startDate;

    @DecimalMin("0.01")
    private BigDecimal price;

    private String notes;

    /**
     * Optional id of a "porta un amico" reward to spend on this subscription. Must belong to
     * the member, be unused and not expired; the discount is applied to the final price and
     * the reward is burned. One per subscription: rewards do not stack.
     */
    private Long rewardId;
}
