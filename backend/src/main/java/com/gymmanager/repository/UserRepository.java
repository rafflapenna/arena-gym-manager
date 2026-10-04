package com.gymmanager.repository;

import com.gymmanager.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    boolean existsByUsername(String username);

    /** Login/recovery lookups ignore case: iOS capitalises the first letter of the username
     *  field and usernames are emails, which are case-insensitive in practice. New accounts
     *  are already stored lowercase; IgnoreCase also covers any legacy mixed-case row. */
    Optional<AppUser> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    Optional<AppUser> findByMemberId(Long memberId);

    List<AppUser> findByRole(String role);
    List<AppUser> findByGymIdAndRole(Long gymId, String role);
    List<AppUser> findByGymIdAndRoleInOrderByLastNameAscFirstNameAsc(Long gymId, Collection<String> roles);
    long countByGymId(Long gymId);
}
