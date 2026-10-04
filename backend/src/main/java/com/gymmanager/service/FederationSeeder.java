package com.gymmanager.service;

import com.gymmanager.entity.Federation;
import com.gymmanager.entity.FederationCardType;
import com.gymmanager.entity.Gym;
import com.gymmanager.repository.FederationCardTypeRepository;
import com.gymmanager.repository.FederationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds a gym's own copy of the federation fee lists, on the same pattern as
 * {@link SubscriptionTypeSeeder}: called once per existing gym at startup and again whenever a
 * gym is created, always idempotently, so it doubles as the migration for gyms that already
 * exist.
 *
 * <p>The amounts below are the published national fees (sources in the constants). They are a
 * <em>starting point</em>, not a rule: each gym owns its copy and the gym manager edits it as
 * the federations update their tariffs. Nothing here is referenced by code — a gym doing judo
 * or karate simply adds its own federation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FederationSeeder {

    private final FederationRepository federationRepository;
    private final FederationCardTypeRepository cardTypeRepository;

    private record CardTemplate(String name, String price) {
    }

    /**
     * @param seasonStartMonth month the season starts on — the two differ and both matter:
     *                         FederKombat runs 1 Sep → 31 Aug, FIJLKAM expires 31 December.
     */
    private record FederationTemplate(String name, int seasonStartMonth, List<CardTemplate> cards) {
    }

    /**
     * FederKombat (kickboxing, muay thai, sambo, MMA) — "Guida Affiliazione e Tesseramenti
     * 2025/2026", §22 "Tipologie di tessere atleta". Season 1 September → 31 August.
     * MMA competitive requires a minimum age of 15, so it falls in the Over 15 line.
     */
    private static final FederationTemplate FEDERKOMBAT = new FederationTemplate(
            "FederKombat", 9,
            List.of(
                    new CardTemplate("Basic", "5.00"),
                    new CardTemplate("Giovanissimi (5-9 anni)", "5.00"),
                    new CardTemplate("Non Agonista (dai 10 anni)", "15.00"),
                    new CardTemplate("Agonistica Under 15", "15.00"),
                    new CardTemplate("Agonistica Over 15", "25.00")
            ));

    /**
     * FIJLKAM (judo, lotta, karate, arti marziali) — "Norme e procedure per l'affiliazione e il
     * tesseramento 2026/2028", Titolo V "Tabella quote federali". Registration expires on
     * 31 December, i.e. the solar year, hence a season starting in month 1.
     */
    private static final FederationTemplate FIJLKAM = new FederationTemplate(
            "FIJLKAM", 1,
            List.of(
                    new CardTemplate("Tessera Promozionale (30 giorni)", "1.00"),
                    new CardTemplate("Atleti PA", "3.00"),
                    new CardTemplate("Atleti Amatore", "10.00"),
                    new CardTemplate("Atleti MMA Amatoriale AG", "20.00"),
                    new CardTemplate("Atleti AG (agonisti)", "30.00"),
                    new CardTemplate("Atleti AG Master", "40.00")
            ));

    private static final List<FederationTemplate> TEMPLATES = List.of(FEDERKOMBAT, FIJLKAM);

    /**
     * Idempotent: a federation the gym already has is left completely alone, fee list included.
     *
     * <p>The fee lines are written only in the same breath as creating the federation, never
     * topped up afterwards. Re-adding "missing" lines on every startup would look harmless but
     * would resurrect a line the gym manager deliberately deleted, every restart, with no way
     * to make it stick. Once the federation exists, its list belongs to the gym.
     */
    @Transactional
    public void ensureDefaultsForGym(Gym gym) {
        for (FederationTemplate template : TEMPLATES) {
            if (federationRepository.existsByGymIdAndNameIgnoreCase(gym.getId(), template.name())) {
                continue;
            }

            Federation federation = federationRepository.save(Federation.builder()
                    .gym(gym)
                    .name(template.name())
                    .seasonStartMonth(template.seasonStartMonth())
                    .active(true)
                    .build());

            for (CardTemplate card : template.cards()) {
                cardTypeRepository.save(FederationCardType.builder()
                        .federation(federation)
                        .name(card.name())
                        .price(new BigDecimal(card.price()))
                        .active(true)
                        .build());
            }
            log.info("Seeded federation '{}' with {} card types for gym id={}",
                    template.name(), template.cards().size(), gym.getId());
        }
    }
}
