package com.gymmanager.repository;

import com.gymmanager.entity.AppUser;
import com.gymmanager.entity.InviteToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InviteTokenRepository extends JpaRepository<InviteToken, Long> {
    Optional<InviteToken> findByTokenAndUsedFalse(String token);

    void deleteByUser(AppUser user);

    /** Discards pending (unredeemed) invites for a user — used before issuing a fresh one. */
    void deleteByUserAndUsedFalse(AppUser user);
}
