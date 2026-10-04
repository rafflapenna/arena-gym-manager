package com.gymmanager.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine-backed cache configuration.
 *
 * <p>Two named caches are declared with separate specs:
 * <ul>
 *   <li><b>userDetails</b> — JWT filter: 60 s TTL, max 10 000 entries.
 *       Eliminates one {@code SELECT users} per authenticated request.
 *       Deactivated users are evicted within ≤ 60 s, which is acceptable.</li>
 *   <li><b>lookups</b> — subscription types, statuses, payment methods, belt grades.
 *       10 min TTL, max 1 000 entries.  Write methods perform allEntries eviction so
 *       newly added lookup values appear immediately on the next read.</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        manager.registerCustomCache("userDetails",
                Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterWrite(60, TimeUnit.SECONDS)
                        .build());

        manager.registerCustomCache("lookups",
                Caffeine.newBuilder()
                        .maximumSize(1_000)
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .build());

        return manager;
    }
}
