package com.gymmanager.service;

import com.gymmanager.entity.Member;
import com.gymmanager.exception.ResourceNotFoundException;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates and persists the opaque QR check-in token on {@link Member} (roadmap #5).
 *
 * <p>The token is a 256-bit {@link SecureRandom} value, base64url-encoded without
 * padding (43 chars) — not a signed JWT, so it stays short, printable on a card, and
 * revocable with a plain UPDATE ({@link #rotate}), with no expiry to track.
 *
 * <p>Scoping mirrors {@code CheckInService.assertMemberInScope}: an unknown member id or
 * one belonging to another gym both surface as a 404 (anti-IDOR, no info disclosure).
 */
@Service
public class CheckinTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final int TOKEN_BYTES = 32; // 256 bits
    private static final int MAX_ATTEMPTS = 5;

    private final MemberRepository memberRepository;
    private final CurrentUser currentUser;
    private final TransactionTemplate requiresNewTransaction;

    public CheckinTokenService(
            MemberRepository memberRepository,
            CurrentUser currentUser,
            PlatformTransactionManager transactionManager
    ) {
        this.memberRepository = memberRepository;
        this.currentUser = currentUser;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");
    }

    /** MEMBER self-service: the current member's own token (backfilled lazily if null). */
    @Transactional
    public String getTokenForCurrentMember() {
        return getToken(currentUser.memberId());
    }

    /** Backoffice: a member's token, scoped to the operator's own gym (backfilled lazily). */
    @Transactional
    public String getToken(Long memberId) {
        Member member = getInScope(memberId);
        String existing = member.getCheckinToken();
        return existing != null ? existing : assignNewToken(member);
    }

    /** Backoffice: rigenerates a member's token — the previous one stops working immediately. */
    @Transactional
    public String rotate(Long memberId) {
        Member member = getInScope(memberId);
        return assignNewToken(member);
    }

    private Member getInScope(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + memberId));
        Long gymId = member.getGym() != null ? member.getGym().getId() : null;
        if (!currentUser.canAccessGym(gymId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
        }
        return member;
    }

    /**
     * Persists a freshly generated token, retrying on the (astronomically rare, 2^-256)
     * unique-constraint collision instead of letting a 409 leak to the caller.
     *
     * <p>Each attempt runs in its own {@code REQUIRES_NEW} transaction via
     * {@link TransactionTemplate} (deliberately programmatic, not a self-invoked
     * {@code @Transactional} method, which the Spring proxy wouldn't honor): once a
     * unique-constraint violation aborts a Postgres transaction, no further statement can
     * run in it until rollback, so the retry needs a genuinely fresh transaction.
     */
    private String assignNewToken(Member member) {
        Long memberId = member.getId();
        DataIntegrityViolationException lastError = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String token = generateToken();
            try {
                requiresNewTransaction.executeWithoutResult(status -> {
                    Member managed = memberRepository.getReferenceById(memberId);
                    managed.setCheckinToken(token);
                    memberRepository.saveAndFlush(managed);
                });
                member.setCheckinToken(token);
                return token;
            } catch (DataIntegrityViolationException e) {
                lastError = e;
            }
        }
        throw lastError;
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }
}
