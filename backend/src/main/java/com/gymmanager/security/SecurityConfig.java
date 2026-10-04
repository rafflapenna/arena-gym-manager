package com.gymmanager.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsServiceImpl userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public
                        .requestMatchers("/api/v1/auth/**", "/actuator/health").permitAll()
                        // Lookup management — ADMIN only
                        .requestMatchers(HttpMethod.POST,   "/api/v1/belt-grades/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,    "/api/v1/belt-grades/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/belt-grades/**").hasRole("ADMIN")
                        // Subscription types: STAFF/GYM_MANAGER only (backoffice "Listino" preset
                        // page, roadmap: wizard "Iscrizione & Incasso" §4 / per-gym listino
                        // migration). Types are per-gym data now; ADMIN has no gym of its own to
                        // manage a price list for (same reasoning as members/subscriptions below).
                        .requestMatchers(HttpMethod.POST,   "/api/v1/subscription-types/**").hasAnyRole("STAFF", "GYM_MANAGER")
                        .requestMatchers(HttpMethod.PUT,    "/api/v1/subscription-types/**").hasAnyRole("STAFF", "GYM_MANAGER")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/subscription-types/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,   "/api/v1/payment-methods/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,    "/api/v1/payment-methods/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/payment-methods/**").hasRole("ADMIN")
                        // MEMBER: only /me area
                        .requestMatchers("/api/v1/me/**").hasRole("MEMBER")
                        // Members / subscriptions / payments / dashboard — STAFF and GYM_MANAGER only.
                        // ADMIN is intentionally excluded: ADMIN manages only gym managers/gyms,
                        // not the members area (read and write).
                        .requestMatchers(HttpMethod.GET, "/api/v1/dashboard/**").hasAnyRole("STAFF", "GYM_MANAGER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/members/**").hasAnyRole("STAFF", "GYM_MANAGER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/subscriptions/**").hasAnyRole("STAFF", "GYM_MANAGER")
                        .requestMatchers(HttpMethod.POST,   "/api/v1/members/**").hasAnyRole("STAFF", "GYM_MANAGER")
                        .requestMatchers(HttpMethod.PUT,    "/api/v1/members/**").hasAnyRole("STAFF", "GYM_MANAGER")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/members/**").hasAnyRole("STAFF", "GYM_MANAGER")
                        .requestMatchers(HttpMethod.PATCH,  "/api/v1/members/**").hasAnyRole("STAFF", "GYM_MANAGER")
                        .requestMatchers("/api/v1/subscriptions/**").hasAnyRole("STAFF", "GYM_MANAGER")
                        // Check-ins collection endpoint (member-scoped check-ins are covered by /members/** above).
                        .requestMatchers("/api/v1/check-ins/**").hasAnyRole("STAFF", "GYM_MANAGER")
                        .requestMatchers("/api/v1/members/*/subscriptions/**").hasAnyRole("STAFF", "GYM_MANAGER")
                        .requestMatchers("/api/v1/members/*/payments/**").hasAnyRole("STAFF", "GYM_MANAGER")
                        // Corsi & Pacchetti (#4) — STAFF/GYM_MANAGER manage the catalog, ADMIN excluded
                        // (same reasoning as members/subscriptions above). Without these explicit
                        // matchers these paths would fall through to anyRequest().authenticated()
                        // and be reachable by ANY authenticated user, including MEMBER/TRAINER.
                        .requestMatchers("/api/v1/disciplines/**").hasAnyRole("STAFF", "GYM_MANAGER")
                        .requestMatchers("/api/v1/courses/**").hasAnyRole("STAFF", "GYM_MANAGER")
                        .requestMatchers("/api/v1/packages/**").hasAnyRole("STAFF", "GYM_MANAGER")
                        // Prove gratuite / lead (roadmap #8) — STAFF/GYM_MANAGER manage the funnel,
                        // ADMIN excluded (same reasoning as members/courses above). Without this
                        // explicit matcher /leads/** would fall through to anyRequest().authenticated()
                        // and be reachable by ANY authenticated user, including ADMIN/MEMBER/TRAINER.
                        .requestMatchers("/api/v1/leads/**").hasAnyRole("STAFF", "GYM_MANAGER")
                        // Quote associative e tesseramenti federali. La lettura serve anche allo
                        // STAFF (incassa alla reception); le sole scritture sui prezzi sono
                        // ulteriormente ristrette a GYM_MANAGER con @PreAuthorize sul controller.
                        .requestMatchers("/api/v1/federations/**").hasAnyRole("STAFF", "GYM_MANAGER")
                        .requestMatchers("/api/v1/fees/**").hasAnyRole("STAFF", "GYM_MANAGER")
                        // Member documents: member-scoped CRUD already falls under the /members/**
                        // matchers above; this one covers the gym-wide "/documents/expiring" listing,
                        // which lives outside the /members prefix and would otherwise fall through to
                        // anyRequest().authenticated() and be reachable by ANY authenticated user.
                        .requestMatchers(HttpMethod.GET, "/api/v1/documents/**").hasAnyRole("STAFF", "GYM_MANAGER")
                        // TRAINER: own courses + attendance of own lessons only (ownership enforced in service).
                        .requestMatchers("/api/v1/trainer/**").hasRole("TRAINER")
                        // Everything else — any authenticated user (lookups GET, /users/** gated per-controller)
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Explicit {@link ProviderManager} instead of the default one
     * {@code AuthenticationConfiguration} builds, so post-authentication credential erasure
     * can be turned off. {@code ProviderManager} erases the authenticated {@code UserDetails}'
     * password after a successful login by default; {@link UserDetailsServiceImpl#loadUserByUsername}
     * is {@code @Cacheable} (60s TTL) and returns the SAME cached instance to every caller
     * (this provider and the JWT filter alike), so that erasure was mutating the cached
     * object in place — every login for that user within the TTL after the first then
     * compared bcrypt against a {@code null} password and got a spurious 401. What's kept
     * around is only the bcrypt hash, never the plaintext password, so retaining it is safe.
     */
    @Bean
    public AuthenticationManager authenticationManager() {
        ProviderManager providerManager = new ProviderManager(authenticationProvider());
        providerManager.setEraseCredentialsAfterAuthentication(false);
        return providerManager;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
