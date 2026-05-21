package com.bank.balancedashboard.integration;

import com.bank.balancedashboard.application.port.out.AccountPort;
import com.bank.balancedashboard.application.port.out.AuditEventPublisher;
import com.bank.balancedashboard.application.port.out.CachePort;
import com.bank.balancedashboard.domain.audit.AuditEventRecord;
import com.bank.balancedashboard.domain.audit.Result;
import com.bank.balancedashboard.domain.model.AccountType;
import com.bank.balancedashboard.domain.model.AccountView;
import com.bank.balancedashboard.domain.model.RankedDashboard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for IDOR guard (ADR-006 acceptance criteria).
 * AC-001-E2, Security C-3.
 *
 * <p>Tests:
 * (1) Tampered X-Customer-Id -> 403 + audit FORBIDDEN + zero AccountPort calls
 * (2) Matching header -> 200 (proceeds normally)
 * (3) Absent header -> 200 (JWT sub is source of truth)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("integration")
class IborGuardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountPort accountPort;

    @MockBean
    private CachePort cachePort;

    @MockBean
    private AuditEventPublisher auditEventPublisher;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static final UUID CUSTOMER_A_ID = UUID.fromString("aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa");
    private static final UUID CUSTOMER_B_ID = UUID.fromString("bbbbbbbb-2222-2222-2222-bbbbbbbbbbbb");

    @BeforeEach
    void setUp() {
        // Default: cache miss, AccountPort returns one account
        when(cachePort.get(any())).thenReturn(Optional.empty());
        when(accountPort.fetchAccounts(any())).thenReturn(List.of(
                new AccountView(1, CUSTOMER_A_ID, "****1234", AccountType.SAVINGS,
                        new BigDecimal("100.00"), "THB", Instant.now(), false, "account.type.savings")
        ));
        doNothing().when(auditEventPublisher).publish(any());
    }

    @Test
    @DisplayName("(1) Tampered X-Customer-Id -> 403 + audit FORBIDDEN + zero AccountPort calls")
    void tamperedHeader_returnsForbidden_andEmitsAuditForbidden() throws Exception {
        // Given: JWT for customer A, header says customer B (tampered)
        mockMvc.perform(get("/api/v1/balance-dashboard")
                        .with(jwt().jwt(jwt -> jwt.subject(CUSTOMER_A_ID.toString())
                                .claim("scope", "accounts:read")))
                        .header("X-Customer-Id", CUSTOMER_B_ID.toString())
                        .accept(MediaType.APPLICATION_JSON))
                // Then: 403 Forbidden
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // AccountPort NEVER called (IDOR guard aborted the chain)
        verify(accountPort, never()).fetchAccounts(any());

        // Audit FORBIDDEN emitted with actorId = JWT sub (A), not header value (B)
        ArgumentCaptor<AuditEventRecord> auditCaptor = ArgumentCaptor.forClass(AuditEventRecord.class);
        verify(auditEventPublisher, times(1)).publish(auditCaptor.capture());
        AuditEventRecord emitted = auditCaptor.getValue();
        assertThat(emitted.result()).isEqualTo(Result.FORBIDDEN);
        assertThat(emitted.actorId()).isEqualTo(CUSTOMER_A_ID); // JWT sub, not header value
    }

    @Test
    @DisplayName("(2) Matching X-Customer-Id -> 200 proceeds normally")
    void matchingHeader_proceedsNormally() throws Exception {
        mockMvc.perform(get("/api/v1/balance-dashboard")
                        .with(jwt().jwt(jwt -> jwt.subject(CUSTOMER_A_ID.toString())
                                .claim("scope", "accounts:read")))
                        .header("X-Customer-Id", CUSTOMER_A_ID.toString()) // matches JWT sub
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts").isArray());
    }

    @Test
    @DisplayName("(3) Absent X-Customer-Id -> 200 — JWT sub is source of truth")
    void absentHeader_proceedsNormally_jwtSubIsSourceOfTruth() throws Exception {
        // No X-Customer-Id header — should proceed, JWT sub is the customerId
        mockMvc.perform(get("/api/v1/balance-dashboard")
                        .with(jwt().jwt(jwt -> jwt.subject(CUSTOMER_A_ID.toString())
                                .claim("scope", "accounts:read")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // AccountPort called with JWT sub UUID
        verify(accountPort, times(1)).fetchAccounts(CUSTOMER_A_ID);
    }
}
