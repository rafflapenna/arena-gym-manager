package com.gymmanager.security;

import com.gymmanager.entity.AppUser;
import com.gymmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Cached for 60 s (see {@code CacheConfig}).  Both call sites — the JWT filter and
     * {@code DaoAuthenticationProvider} — inject this bean by its Spring proxy, so the
     * {@code @Cacheable} advice is active on every call.
     */
    @Override
    @Cacheable(value = "userDetails", key = "#username")
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser appUser = userRepository.findByUsernameIgnoreCase(username == null ? "" : username.trim())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new UsernameNotFoundException("User not found or inactive: " + username));

        return User.builder()
                .username(appUser.getUsername())
                .password(appUser.getPassword())
                .roles(appUser.getRole())
                .build();
    }
}
