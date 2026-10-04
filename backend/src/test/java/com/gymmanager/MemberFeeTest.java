package com.gymmanager;

import com.gymmanager.dto.FederationResponseDTO;
import com.gymmanager.dto.MemberFederationCardRequestDTO;
import com.gymmanager.dto.MemberFeeRequestDTO;
import com.gymmanager.dto.MemberFeeResponseDTO;
import com.gymmanager.dto.MemberRequestDTO;
import com.gymmanager.dto.PersonalInfoRequestDTO;
import com.gymmanager.entity.Gym;
import com.gymmanager.repository.FederationCardTypeRepository;
import com.gymmanager.repository.FederationRepository;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.exception.ConflictException;
import com.gymmanager.repository.MemberFederationCardRepository;
import com.gymmanager.repository.MemberFeeRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.security.AuthPrincipal;
import com.gymmanager.service.FederationSeeder;
import com.gymmanager.service.FederationService;
import com.gymmanager.service.MemberFeeService;
import com.gymmanager.service.MemberService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Quota associativa and federation registration.
 *
 * The rules worth pinning are the two expiry policies: the gym fee runs 12 months from the day
 * it is paid, while a federation registration runs to the end of that federation's season — and
 * the seasons genuinely differ (FederKombat 1 Sep → 31 Aug, FIJLKAM the solar year). Getting
 * either wrong only shows up months later, as wrong renewal dates.
 */
@SpringBootTest
@Testcontainers
class MemberFeeTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MemberService memberService;
    @Autowired MemberFeeService memberFeeService;
    @Autowired FederationService federationService;
    @Autowired FederationSeeder federationSeeder;

    @Autowired GymRepository gymRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired MemberFeeRepository memberFeeRepository;
    @Autowired MemberFederationCardRepository memberFederationCardRepository;
    @Autowired FederationRepository federationRepository;
    @Autowired FederationCardTypeRepository cardTypeRepository;

    private Gym gym;

    @BeforeEach
    void setUp() {
        memberFeeRepository.deleteAll();
        memberFederationCardRepository.deleteAll();
        memberRepository.deleteAll();

        gym = gymRepository.save(Gym.builder().name("Gym " + System.nanoTime()).build());
        actAs(gym.getId(), "GYM_MANAGER");
        federationSeeder.ensureDefaultsForGym(gym);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void actAs(Long gymId, String role) {
        var auth = new UsernamePasswordAuthenticationToken(
                "tester", null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(new AuthPrincipal(1L, null, gymId, role));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Long member(String email) {
        PersonalInfoRequestDTO pi = new PersonalInfoRequestDTO();
        pi.setFirstName("Test");
        pi.setLastName("Member");
        pi.setEmail(email);
        pi.setPhone("3330000000");
        pi.setBirthDate(LocalDate.of(1990, 1, 1));
        MemberRequestDTO req = new MemberRequestDTO();
        req.setPersonalInfo(pi);
        return memberService.create(req).getId();
    }

    private FederationResponseDTO federation(String name) {
        return federationService.findAll().stream()
                .filter(f -> f.getName().equalsIgnoreCase(name))
                .findFirst().orElseThrow();
    }

    private MemberFeeResponseDTO charge(Long memberId, String type, Long cardTypeId, LocalDate paidOn) {
        MemberFeeRequestDTO dto = new MemberFeeRequestDTO();
        dto.setType(type);
        dto.setCardTypeId(cardTypeId);
        dto.setPaidOn(paidOn);
        return memberFeeService.charge(memberId, dto);
    }

    // ── Seed ─────────────────────────────────────────────────────────────

    @Test
    void seeder_createsBothFederationsWithTheirRealFeeLists() {
        FederationResponseDTO fk = federation("FederKombat");
        FederationResponseDTO fj = federation("FIJLKAM");

        assertThat(fk.getSeasonStartMonth()).isEqualTo(9);   // 1 settembre -> 31 agosto
        assertThat(fj.getSeasonStartMonth()).isEqualTo(1);   // scade il 31 dicembre

        assertThat(fk.getCardTypes()).extracting("name")
                .contains("Agonistica Over 15", "Non Agonista (dai 10 anni)");
        assertThat(fk.getCardTypes()).filteredOn("name", "Agonistica Over 15")
                .first().extracting("price").isEqualTo(new BigDecimal("25.00"));
        assertThat(fj.getCardTypes()).filteredOn("name", "Atleti AG (agonisti)")
                .first().extracting("price").isEqualTo(new BigDecimal("30.00"));
    }

    @Test
    void seeder_isIdempotentAndDoesNotResurrectDeletedCardTypes() {
        FederationResponseDTO fk = federation("FederKombat");
        Long toDelete = fk.getCardTypes().get(0).getId();
        federationService.deleteCardType(toDelete);
        int remaining = federation("FederKombat").getCardTypes().size();

        federationSeeder.ensureDefaultsForGym(gym);

        // Re-running must not bring back a line the manager deleted on purpose.
        assertThat(federation("FederKombat").getCardTypes()).hasSize(remaining);
        assertThat(federationRepository.findByGymIdOrderByNameAsc(gym.getId())).hasSize(2);
    }

    // ── Scadenze ─────────────────────────────────────────────────────────

    @Test
    void associationFee_lasts12MonthsFromThePaymentDate() {
        Long m = member("assoc@test.com");
        MemberFeeResponseDTO fee = charge(m, "ASSOCIATION", null, LocalDate.of(2026, 3, 15));

        assertThat(fee.getValidUntil()).isEqualTo(LocalDate.of(2027, 3, 14));
        // Prende la quota configurata sulla palestra, che di default e' 20 euro.
        assertThat(fee.getAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void federationFee_runsToTheEndOfThatFederationsSeason_notTwelveMonths() {
        Long m = member("fed@test.com");
        Long fkCard = federation("FederKombat").getCardTypes().get(0).getId();
        Long fjCard = federation("FIJLKAM").getCardTypes().get(0).getId();

        // FederKombat: pagato a marzo, la stagione e' quella iniziata il 1 settembre prima.
        assertThat(charge(m, "FEDERATION", fkCard, LocalDate.of(2026, 3, 15)).getValidUntil())
                .isEqualTo(LocalDate.of(2026, 8, 31));
        // ...e pagato a ottobre copre la stagione appena iniziata.
        assertThat(charge(m, "FEDERATION", fkCard, LocalDate.of(2026, 10, 2)).getValidUntil())
                .isEqualTo(LocalDate.of(2027, 8, 31));

        // FIJLKAM: anno solare, scade il 31 dicembre qualunque sia il mese di pagamento.
        assertThat(charge(m, "FEDERATION", fjCard, LocalDate.of(2026, 3, 15)).getValidUntil())
                .isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(charge(m, "FEDERATION", fjCard, LocalDate.of(2026, 10, 2)).getValidUntil())
                .isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void federationFee_snapshotsThePriceSoLaterChangesDoNotRewriteIt() {
        Long m = member("snap@test.com");
        var card = federation("FederKombat").getCardTypes().get(0);
        charge(m, "FEDERATION", card.getId(), LocalDate.now());

        var update = new com.gymmanager.dto.FederationCardTypeRequestDTO();
        update.setName(card.getName());
        update.setPrice(new BigDecimal("99.00"));
        federationService.updateCardType(card.getId(), update);

        assertThat(memberFeeService.findByMember(m).get(0).getAmount())
                .isEqualByComparingTo(card.getPrice());
    }

    @Test
    void federationFee_withoutACardType_returns422() {
        Long m = member("nocard@test.com");
        assertThatThrownBy(() -> charge(m, "FEDERATION", null, LocalDate.now()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(422));
    }

    @Test
    void cardType_alreadyChargedCannotBeDeleted() {
        Long m = member("charged@test.com");
        Long cardId = federation("FederKombat").getCardTypes().get(0).getId();
        charge(m, "FEDERATION", cardId, LocalDate.now());

        // Cancellarlo riscriverebbe la storia di una quota gia' incassata.
        assertThatThrownBy(() -> federationService.deleteCardType(cardId))
                .isInstanceOf(com.gymmanager.exception.ConflictException.class);
    }

    // ── Scoping ──────────────────────────────────────────────────────────

    @Test
    void federation_ofAnotherGymLooksLikeNotFound() {
        Long foreignCardId = federation("FederKombat").getCardTypes().get(0).getId();

        Gym other = gymRepository.save(Gym.builder().name("Altra " + System.nanoTime()).build());
        actAs(other.getId(), "GYM_MANAGER");

        assertThatThrownBy(() -> federationService.getCardType(foreignCardId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void expiringFees_listsOnlyThoseInTheWindow() {
        Long m = member("exp@test.com");
        // Scade tra ~10 giorni: pagata 12 mesi meno 10 giorni fa.
        charge(m, "ASSOCIATION", null, LocalDate.now().minusYears(1).plusDays(11));

        assertThat(memberFeeService.findExpiring(30)).hasSize(1);
        assertThat(memberFeeService.findExpiring(5)).isEmpty();
    }

    // ── Card numbers ─────────────────────────────────────────────────────

    private static MemberFederationCardRequestDTO card(String number) {
        MemberFederationCardRequestDTO dto = new MemberFederationCardRequestDTO();
        dto.setCardNumber(number);
        return dto;
    }

    @Test
    void cardNumber_isOnePerFederationAndCorrectingItOverwrites() {
        Long memberId = member("card@test.it");
        Long fk = federation("FederKombat").getId();

        memberFeeService.saveCard(memberId, fk, card(" 483572 "));
        memberFeeService.saveCard(memberId, fk, card("483573"));

        assertThat(memberFeeService.findCards(memberId))
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.getCardNumber()).isEqualTo("483573");
                    assertThat(c.getFederation().name()).isEqualTo("FederKombat");
                });

        memberFeeService.deleteCard(memberId, fk);
        assertThat(memberFeeService.findCards(memberId)).isEmpty();
    }

    @Test
    void cardNumber_cannotBelongToTwoMembersOfTheSameFederation() {
        Long a = member("a@test.it");
        Long b = member("b@test.it");
        Long fk = federation("FederKombat").getId();
        memberFeeService.saveCard(a, fk, card("483572"));

        assertThatThrownBy(() -> memberFeeService.saveCard(b, fk, card("483572")))
                .isInstanceOf(ConflictException.class);
        // Same number with another federation is a different card.
        memberFeeService.saveCard(b, federation("FIJLKAM").getId(), card("483572"));
    }

    @Test
    void cardNumber_federationOfAnotherGymIsNotFound() {
        Long memberId = member("scope@test.it");
        Gym other = gymRepository.save(Gym.builder().name("Other " + System.nanoTime()).build());
        federationSeeder.ensureDefaultsForGym(other);
        Long foreignFk = federationRepository.findByGymIdOrderByNameAsc(other.getId()).get(0).getId();

        assertThatThrownBy(() -> memberFeeService.saveCard(memberId, foreignFk, card("1")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void deletingTheMember_removesTheirCardNumbers() {
        Long memberId = member("gone@test.it");
        memberFeeService.saveCard(memberId, federation("FederKombat").getId(), card("483572"));

        memberService.delete(memberId);

        assertThat(memberFederationCardRepository.count()).isZero();
    }
}
