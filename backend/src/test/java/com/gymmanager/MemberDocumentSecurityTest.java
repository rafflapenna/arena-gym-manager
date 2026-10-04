package com.gymmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymmanager.dto.MemberRequestDTO;
import com.gymmanager.dto.PersonalInfoRequestDTO;
import com.gymmanager.entity.Gym;
import com.gymmanager.repository.GymRepository;
import com.gymmanager.repository.MemberDocumentRepository;
import com.gymmanager.repository.MemberRepository;
import com.gymmanager.security.AuthPrincipal;
import com.gymmanager.service.MemberService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Member documents (medical certs + generic attachments): route-level security/IDOR coverage.
 * Uses a real {@link AuthPrincipal} (not {@code @WithMockUser}) since the service layer needs
 * a resolvable gymId/memberId, which a role-only mock user doesn't provide.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MemberDocumentSecurityTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired GymRepository gymRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired MemberDocumentRepository memberDocumentRepository;
    @Autowired MemberService memberService;

    private Long gymAId;
    private Long gymBId;

    @BeforeEach
    void setUp() {
        memberDocumentRepository.deleteAll();
        memberRepository.deleteAll();
        gymAId = gymRepository.save(Gym.builder().name("Gym A").build()).getId();
        gymBId = gymRepository.save(Gym.builder().name("Gym B").build()).getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        memberDocumentRepository.deleteAll();
        memberRepository.deleteAll();
    }

    private RequestPostProcessor as(String role, Long gymId) {
        var auth = new UsernamePasswordAuthenticationToken(
                "tester", null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(new AuthPrincipal(1L, null, gymId, role));
        return authentication(auth);
    }

    private RequestPostProcessor asMember(Long memberId, Long gymId) {
        var auth = new UsernamePasswordAuthenticationToken(
                "tester", null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
        auth.setDetails(new AuthPrincipal(1L, memberId, gymId, "MEMBER"));
        return authentication(auth);
    }

    private Long createMember(Long gymId, String email) {
        var auth = new UsernamePasswordAuthenticationToken(
                "tester", null, List.of(new SimpleGrantedAuthority("ROLE_GYM_MANAGER")));
        auth.setDetails(new AuthPrincipal(1L, null, gymId, "GYM_MANAGER"));
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            PersonalInfoRequestDTO pi = new PersonalInfoRequestDTO();
            pi.setFirstName("Test");
            pi.setLastName("Member");
            pi.setEmail(email);
            pi.setPhone("3330000000");
            pi.setBirthDate(LocalDate.of(1990, 1, 1));
            MemberRequestDTO req = new MemberRequestDTO();
            req.setPersonalInfo(pi);
            return memberService.create(req).getId();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private MockMultipartFile pdfFile() {
        return new MockMultipartFile("file", "cert.pdf", "application/pdf", "hello world".getBytes());
    }

    private Long uploadDocument(Long memberId, Long gymId, String type, String title) throws Exception {
        var builder = multipart("/api/v1/members/{id}/documents", memberId)
                .file(pdfFile())
                .param("type", type)
                .with(as("STAFF", gymId));
        if (title != null) builder.param("title", title);
        var result = mockMvc.perform(builder)
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void adminAndMember_areForbiddenOnExpiringEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/documents/expiring").with(as("ADMIN", gymAId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/documents/expiring").with(as("MEMBER", gymAId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberAndTrainer_areForbiddenOnMemberScopedDocumentRoutes() throws Exception {
        Long memberId = createMember(gymAId, "scoped1@test.com");

        mockMvc.perform(get("/api/v1/members/{id}/documents", memberId).with(as("MEMBER", gymAId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/members/{id}/documents", memberId).with(as("TRAINER", gymAId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/v1/members/{id}/documents", memberId)
                        .file(pdfFile()).param("type", "OTHER").param("title", "Doc")
                        .with(as("MEMBER", gymAId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/members/{id}/documents/{docId}", memberId, 999L)
                        .with(as("TRAINER", gymAId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffOfAnotherGym_memberNotFound() throws Exception {
        Long memberId = createMember(gymAId, "crossgym@test.com");

        mockMvc.perform(get("/api/v1/members/{id}/documents", memberId).with(as("STAFF", gymBId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(multipart("/api/v1/members/{id}/documents", memberId)
                        .file(pdfFile()).param("type", "OTHER").param("title", "Doc")
                        .with(as("STAFF", gymBId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadOfDocumentBelongingToAnotherMember_sameGym_returns404() throws Exception {
        Long member1 = createMember(gymAId, "m1@test.com");
        Long member2 = createMember(gymAId, "m2@test.com");
        Long docId = uploadDocument(member1, gymAId, "OTHER", "Contratto");

        mockMvc.perform(get("/api/v1/members/{id}/documents/{docId}/download", member2, docId)
                        .with(as("STAFF", gymAId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/members/{id}/documents/{docId}", member2, docId)
                        .with(as("STAFF", gymAId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void meDocuments_seesOnlyOwnDocuments_foreignDocIdReturns404() throws Exception {
        Long member1 = createMember(gymAId, "self1@test.com");
        Long member2 = createMember(gymAId, "self2@test.com");
        uploadDocument(member1, gymAId, "OTHER", "Mio documento");
        uploadDocument(member2, gymAId, "OTHER", "Documento altrui");

        mockMvc.perform(get("/api/v1/me/documents").with(asMember(member1, gymAId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Mio documento"));

        // member1 trying to download member2's document via its docId must 404, not leak it.
        Long member2DocId = memberDocumentRepository.findByMemberIdOrderByUploadedAtDesc(member2).get(0).getId();
        mockMvc.perform(get("/api/v1/me/documents/{docId}/download", member2DocId).with(asMember(member1, gymAId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadWithDisallowedMimeType_returns422() throws Exception {
        Long memberId = createMember(gymAId, "badmime@test.com");
        MockMultipartFile textFile = new MockMultipartFile("file", "notes.txt", "text/plain", "hi".getBytes());

        mockMvc.perform(multipart("/api/v1/members/{id}/documents", memberId)
                        .file(textFile).param("type", "OTHER").param("title", "Note")
                        .with(as("STAFF", gymAId)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void uploadOther_withoutTitle_returns422() throws Exception {
        Long memberId = createMember(gymAId, "notitle@test.com");

        mockMvc.perform(multipart("/api/v1/members/{id}/documents", memberId)
                        .file(pdfFile()).param("type", "OTHER")
                        .with(as("STAFF", gymAId)))
                .andExpect(status().isUnprocessableEntity());
    }
}
