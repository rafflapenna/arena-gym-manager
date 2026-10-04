package com.gymmanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymmanager.dto.ComuneDTO;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * In-memory registry of Italian comuni for the address autocomplete.
 *
 * <p>Seeded at startup from a bundled JSON (always available, works offline),
 * then refreshed from a stable CDN copy of the ISTAT-derived dataset — at startup
 * and monthly — so the list stays current with zero manual maintenance. If the CDN
 * is unreachable the last good list is kept, so the endpoint never goes empty.
 */
@Service
@Slf4j
public class ComuniService {

    /** Stable, versioned CDN copy of the comuni dataset (same shape as the bundled seed). */
    private static final String CDN_URL = "https://cdn.jsdelivr.net/npm/comuni-json/comuni.json";
    private static final String SEED_RESOURCE = "data/comuni-seed.json";

    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    private volatile List<ComuneDTO> comuni = List.of();

    public ComuniService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void seedFromBundle() {
        try (InputStream is = new ClassPathResource(SEED_RESOURCE).getInputStream()) {
            comuni = parse(is.readAllBytes());
            log.info("Seeded {} comuni from bundled resource", comuni.size());
        } catch (Exception e) {
            log.warn("Could not load bundled comuni seed: {}", e.getMessage());
        }
    }

    /**
     * Try a fresh copy as soon as the app is up — on a daemon thread so it never
     * blocks startup (the bundled seed is already serving) and never slows tests.
     */
    @EventListener(ApplicationReadyEvent.class)
    void refreshOnStartup() {
        Thread t = new Thread(this::refreshFromCdn, "comuni-refresh");
        t.setDaemon(true);
        t.start();
    }

    /** Monthly refresh: 04:00 on the 1st of each month. */
    @Scheduled(cron = "0 0 4 1 * *")
    public void refreshFromCdn() {
        try {
            byte[] body = restClient.get().uri(CDN_URL).retrieve().body(byte[].class);
            List<ComuneDTO> fresh = parse(body);
            if (!fresh.isEmpty()) {
                comuni = fresh;
                log.info("Refreshed {} comuni from CDN", fresh.size());
            }
        } catch (Exception e) {
            log.warn("Comuni CDN refresh failed, keeping current {} entries: {}", comuni.size(), e.getMessage());
        }
    }

    public List<ComuneDTO> all() {
        return comuni;
    }

    private List<ComuneDTO> parse(byte[] json) throws IOException {
        JsonNode arr = objectMapper.readTree(json);
        List<ComuneDTO> out = new ArrayList<>(arr.size());
        for (JsonNode n : arr) {
            List<String> caps = new ArrayList<>();
            JsonNode capNode = n.get("cap");
            if (capNode != null && capNode.isArray()) {
                capNode.forEach(c -> caps.add(c.asText()));
            }
            out.add(new ComuneDTO(
                    text(n, "nome"),
                    text(n, "sigla"),
                    n.path("provincia").path("nome").asText(null),
                    n.path("regione").path("nome").asText(null),
                    caps,
                    text(n, "codiceCatastale")
            ));
        }
        out.sort(Comparator.comparing(ComuneDTO::nome, Comparator.nullsLast(String::compareToIgnoreCase)));
        return out;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
