package com.gymmanager.service;

import com.gymmanager.entity.CheckIn;
import com.gymmanager.repository.CheckInRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolates the actual check-in INSERT in its own transaction, split out of
 * {@link CheckInService} for the same reason {@link MailerService} was split out of
 * {@link InviteService}: self-invocation from within the same bean bypasses Spring's AOP
 * proxy, so a different propagation on a method of the SAME class would silently have no
 * effect.
 *
 * <p>Needed so {@link CheckInService#checkIn} can gracefully recover from the daily-idempotency
 * race that the {@code ux_check_ins_member_day} unique index (round 3 #4) guards against: once
 * any statement inside a Postgres transaction errors, the whole transaction is aborted and
 * every subsequent statement on it fails with "current transaction is aborted" — so re-querying
 * the winning row on the SAME transaction/connection as the failed insert isn't possible.
 * Running the insert attempt on its own {@code REQUIRES_NEW} transaction means only that one
 * rolls back on a constraint violation; the caller's own transaction/connection stays healthy
 * and can safely re-query and return the concurrently-committed row instead.
 */
@Service
@RequiredArgsConstructor
public class CheckInInsertService {

    private final CheckInRepository checkInRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CheckIn insert(CheckIn checkIn) {
        return checkInRepository.save(checkIn);
    }
}
