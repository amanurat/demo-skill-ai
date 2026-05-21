package com.bank.balancedashboard.integration;

import com.bank.balancedashboard.application.port.out.AccountPort;
import com.bank.balancedashboard.application.port.out.AuditEventPublisher;
import com.bank.balancedashboard.domain.audit.AuditEventRecord;
import com.bank.balancedashboard.domain.audit.Result;
import com.bank.balancedashboard.domain.model.AccountType;
import com.bank.balancedashboard.domain.model.AccountView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for BR-014: audit emitted regardless of cache state (AC-001-H3, AC-003-H3).
 *
 * <p>R-BE-009/010 fix: wired with real Testcontainers Redis and Kafka so that
 * the cache-hit path and audit emission are verified against real infra, not mocks.
 *
 * <p>Tests:
 * (1) Warm-cache request -> response from Redis -> audit STILL emitted with cacheHit=true (BR-014)
 * (2) Empty accounts -> audit emitted with accountCount=0 (AC-001-E1)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@Testcontainers
class CacheHitAuditEmittedTest {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @Container
    @SuppressWarnings("resource")
    static final ConfluentKafkaContainer kafka =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.ssl.enabled", () -> "false"); // Testcontainers Redis has no TLS surface
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountPort accountPort;

    @MockBean
    private AuditEventPublisher auditEventPublisher;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    /**
     * R-BE-204 fix: two-request test — first request populates Redis (cache MISS),
     * second request is served from Redis (cache HIT). Asserts that:
     * <ul>
     *   <li>Request 1 returns X-Cache: MISS and audit is emitted with cacheHit=false</li>
     *   <li>Request 2 returns X-Cache: HIT and audit is emitted with cacheHit=true (BR-014)</li>
     *   <li>AuditEventPublisher.publish() is called TWICE in total</li>
     * </ul>
     *
     * <p>Real Testcontainers Redis is used — Redis IS available so the second request
     * genuinely hits the warm cache (as opposed to the iteration-1 "fail-open" comment).
     */
    @Test
    @DisplayName("(1) Second request is cache HIT -> audit still emitted with cacheHit=true (BR-014)")
    void secondRequest_isCacheHit_andAuditStillEmitted() throws Exception {
        // Given: AccountPort returns one account (will be cached after first request)
        when(accountPort.fetchAccounts(any())).thenReturn(List.of(
                new AccountView(1, UUID.randomUUID(), "****5678", AccountType.SAVINGS,
                        new BigDecimal("50000.00"), "THB", Instant.now(), false, "account.type.savings")
        ));
        doNothing().when(auditEventPublisher).publish(any());

        // Request 1 — cache MISS: populates Redis
        mockMvc.perform(get("/api/v1/balance-dashboard")
                        .with(jwt().jwt(j -> j.subject(CUSTOMER_ID.toString())
                                .claim("scope", "accounts:read")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache", "MISS"));

        // Audit emitted once for the MISS — cacheHit=false
        ArgumentCaptor<AuditEventRecord> captor1 = ArgumentCaptor.forClass(AuditEventRecord.class);
        verify(auditEventPublisher, times(1)).publish(captor1.capture());
        assertThat(captor1.getValue().result()).isEqualTo(Result.SUCCESS);
        assertThat(captor1.getValue().cacheHit()).isFalse();
        assertThat(captor1.getValue().accountCount()).isEqualTo(1);

        // Request 2 — cache HIT: served from Redis (real Testcontainers Redis container)
        mockMvc.perform(get("/api/v1/balance-dashboard")
                        .with(jwt().jwt(j -> j.subject(CUSTOMER_ID.toString())
                                .claim("scope", "accounts:read")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Cache", "HIT"));

        // Audit emitted a SECOND time for the HIT — cacheHit=true (BR-014: always emitted)
        ArgumentCaptor<AuditEventRecord> captor2 = ArgumentCaptor.forClass(AuditEventRecord.class);
        verify(auditEventPublisher, times(2)).publish(captor2.capture());
        List<AuditEventRecord> allAuditEvents = captor2.getAllValues();
        assertThat(allAuditEvents).hasSize(2);
        // Second event must have cacheHit=true
        assertThat(allAuditEvents.get(1).cacheHit()).isTrue();
        assertThat(allAuditEvents.get(1).result()).isEqualTo(Result.SUCCESS);
        // AccountPort must NOT be called again on cache hit (only once total)
        verify(accountPort, times(1)).fetchAccounts(any());
    }

    @Test
    @DisplayName("(2) Empty accounts -> audit emitted with accountCount=0 (AC-001-E1)")
    void emptyAccounts_auditEmittedWithAccountCount0() throws Exception {
        // Given: no eligible accounts
        when(accountPort.fetchAccounts(any())).thenReturn(List.of());
        doNothing().when(auditEventPublisher).publish(any());

        // When
        mockMvc.perform(get("/api/v1/balance-dashboard")
                        .with(jwt().jwt(j -> j.subject(CUSTOMER_ID.toString())
                                .claim("scope", "accounts:read")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts").isArray())
                .andExpect(jsonPath("$.accounts").isEmpty())
                .andExpect(jsonPath("$.meta.accountCount").value(0));

        // Audit emitted with accountCount=0
        ArgumentCaptor<AuditEventRecord> captor = ArgumentCaptor.forClass(AuditEventRecord.class);
        verify(auditEventPublisher, times(1)).publish(captor.capture());
        assertThat(captor.getValue().accountCount()).isEqualTo(0);
        assertThat(captor.getValue().result()).isEqualTo(Result.SUCCESS);
    }
}
