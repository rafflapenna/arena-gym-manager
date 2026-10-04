package com.gymmanager.config;

import com.gymmanager.entity.AppUser;
import com.gymmanager.entity.BeltGrade;
import com.gymmanager.entity.Gym;
import com.gymmanager.entity.Member;
import com.gymmanager.entity.PaymentMethod;
import com.gymmanager.entity.SubscriptionStatus;
import com.gymmanager.repository.BeltGradeRepository;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.repository.PaymentMethodRepository;
import com.gymmanager.repository.SubscriptionStatusRepository;
import com.gymmanager.repository.UserRepository;
import com.gymmanager.service.FederationSeeder;
import com.gymmanager.service.SubscriptionTypeSeeder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final SubscriptionTypeSeeder subscriptionTypeSeeder;
    private final FederationSeeder federationSeeder;
    private final SubscriptionStatusRepository subscriptionStatusRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final BeltGradeRepository beltGradeRepository;
    private final UserRepository userRepository;
    private final GymRepository gymRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    /** Demo credentials (admin/admin, instructor/instructor, staff/staff123) are seeded
     *  idempotently at startup when this flag is true. Defaults to false (see application.yml:
     *  APP_SEED_DEMO_USERS) so prod never gets known credentials seeded on a fresh DB; the dev
     *  and test profiles turn it back on. */
    @Value("${app.seed.demo-users:false}")
    private boolean seedDemoUsers;

    private static final String DEFAULT_GYM_NAME = "Sede Principale";

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedSubscriptionStatuses();
        seedPaymentMethods();
        seedBeltGrades();
        Gym defaultGym = seedDefaultGym();
        // Subscription types are per-gym (roadmap: per-gym listino migration): every gym gets
        // its own copy of the defaults, idempotently, instead of a single set of global rows.
        gymRepository.findAll().forEach(subscriptionTypeSeeder::ensureDefaultsForGym);
        gymRepository.findAll().forEach(federationSeeder::ensureDefaultsForGym);
        if (seedDemoUsers) {
            seedUsers(defaultGym);
        } else {
            log.info("app.seed.demo-users=false: skipping demo user seeding");
        }
        backfillGyms(defaultGym);
    }

    private Gym seedDefaultGym() {
        return gymRepository.findByName(DEFAULT_GYM_NAME)
                .orElseGet(() -> {
                    Gym gym = gymRepository.save(Gym.builder().name(DEFAULT_GYM_NAME).build());
                    log.info("Seeded default gym: {} (id={})", DEFAULT_GYM_NAME, gym.getId());
                    return gym;
                });
    }

    /** Assign existing staff/instructor users and members that have no gym to the default gym. */
    private void backfillGyms(Gym defaultGym) {
        for (Member m : memberRepository.findAll()) {
            if (m.getGym() == null) {
                m.setGym(defaultGym);
                memberRepository.save(m);
            }
        }
        for (AppUser u : userRepository.findAll()) {
            boolean changed = false;

            // Gym backfill
            if (u.getGym() == null) {
                if (u.getMember() != null) {
                    u.setGym(u.getMember().getGym());        // MEMBER inherits its member's gym
                    changed = true;
                } else if ("STAFF".equals(u.getRole()) || "GYM_MANAGER".equals(u.getRole())
                        || "INSTRUCTOR".equals(u.getRole())) {
                    // "INSTRUCTOR" recognized during the role-rename transition window (roadmap §4).
                    u.setGym(defaultGym);
                    changed = true;
                }
            }

            // Name backfill (display name for users created before names existed)
            if (u.getFirstName() == null || u.getFirstName().isBlank()) {
                if (u.getMember() != null) {
                    u.setFirstName(u.getMember().getPersonalInfo().getFirstName());
                    u.setLastName(u.getMember().getPersonalInfo().getLastName());
                } else {
                    switch (u.getUsername()) {
                        case "admin"      -> { u.setFirstName("Amministratore"); u.setLastName(""); }
                        case "instructor" -> { u.setFirstName("Istruttore");     u.setLastName("Demo"); }
                        case "staff"      -> { u.setFirstName("Staff");          u.setLastName("Demo"); }
                        default           -> { /* leave null; will fall back to username */ }
                    }
                }
                changed = changed || u.getFirstName() != null;
            }

            if (changed) userRepository.save(u);
        }
    }

    private void seedSubscriptionStatuses() {
        seedStatus("ACTIVE", "Active");
        seedStatus("EXPIRED", "Expired");
        seedStatus("CANCELLED", "Cancelled");
    }

    private void seedPaymentMethods() {
        seedMethod("CASH", "Cash");
    }

    private void seedStatus(String code, String label) {
        if (!subscriptionStatusRepository.existsByCode(code)) {
            subscriptionStatusRepository.save(
                    SubscriptionStatus.builder().code(code).label(label).build()
            );
            log.info("Seeded subscription status: {}", code);
        }
    }

    private void seedMethod(String code, String label) {
        if (!paymentMethodRepository.existsByCode(code)) {
            paymentMethodRepository.save(
                    PaymentMethod.builder().code(code).label(label).build()
            );
            log.info("Seeded payment method: {}", code);
        }
    }

    private void seedBeltGrades() {
        seedBeltGrade("WHITE",       "Bianca",          1);
        seedBeltGrade("YELLOW_HALF", "Mezza Gialla",    2);
        seedBeltGrade("YELLOW",      "Gialla",          3);
        seedBeltGrade("ORANGE_HALF", "Mezza Arancione", 4);
        seedBeltGrade("ORANGE",      "Arancione",       5);
        seedBeltGrade("GREEN_HALF",  "Mezza Verde",     6);
        seedBeltGrade("GREEN",       "Verde",           7);
        seedBeltGrade("BLUE_HALF",   "Mezza Blu",       8);
        seedBeltGrade("BLUE",        "Blu",             9);
        seedBeltGrade("BROWN_HALF",  "Mezza Marrone",  10);
        seedBeltGrade("BROWN",       "Marrone",        11);
        seedBeltGrade("BLACK",       "Nera",           12);
    }

    private void seedBeltGrade(String code, String label, int displayOrder) {
        beltGradeRepository.findByCode(code).ifPresentOrElse(
                existing -> {
                    boolean changed = !label.equals(existing.getLabel())
                            || displayOrder != existing.getDisplayOrder();
                    if (changed) {
                        existing.setLabel(label);
                        existing.setDisplayOrder(displayOrder);
                        beltGradeRepository.save(existing);
                        log.info("Updated belt grade: {} -> label={}, order={}", code, label, displayOrder);
                    }
                },
                () -> {
                    beltGradeRepository.save(
                            BeltGrade.builder().code(code).label(label).displayOrder(displayOrder).build()
                    );
                    log.info("Seeded belt grade: {} (order={})", code, displayOrder);
                }
        );
    }

    private void seedUsers(Gym defaultGym) {
        // Username "instructor" is kept as-is on purpose (only its role changed to
        // GYM_MANAGER — roadmap §4); no trainer demo user or courses/disciplines/packages
        // are seeded here since those are per-gym data created by staff, not fixtures.
        seedUser("admin",      "admin",       "ADMIN",       null,       "Amministratore", "");
        seedUser("instructor", "instructor",  "GYM_MANAGER", defaultGym, "Istruttore",     "Demo");
        seedUser("staff",      "staff123",    "STAFF",       defaultGym, "Staff",          "Demo");
    }

    private void seedUser(String username, String rawPassword, String role, Gym gym,
                          String firstName, String lastName) {
        if (!userRepository.existsByUsername(username)) {
            userRepository.save(AppUser.builder()
                    .username(username)
                    .password(passwordEncoder.encode(rawPassword))
                    .role(role)
                    .active(true)
                    .gym(gym)
                    .firstName(firstName)
                    .lastName(lastName)
                    .build());
            log.info("Seeded user: {} (role={})", username, role);
        }
    }
}
