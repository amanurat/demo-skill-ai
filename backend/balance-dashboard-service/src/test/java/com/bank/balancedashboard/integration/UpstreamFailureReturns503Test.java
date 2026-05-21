package com.bank.balancedashboard.integration;

import com.bank.balancedashboard.application.port.out.AccountPort;
import com.bank.balancedashboard.application.port.out.AuditEventPublisher;
import com.bank.balancedashboard.domain.audit.AuditEventRecord;
import com.bank.balancedashboard.domain.audit.Result;
import com.bank.balancedashboard.domain.exception.DashboardUnavailableException;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test: upstream failure -> 503 Problem-Detail + audit ERROR emitted.
 * AC-003-E1 (partially), impl-notes §3 failure mode contract.
 *
 * <p>R-BE-009/010 fix: wired with real Testcontainers Redis and Kafka so that
 * the full application context (including cache fail-open) is exercised.
 *
 * <p>Tests:
 * (1) CB-OPEN scenario -> 503 + Problem-Detail + Retry-After + audit ERROR
 * (2) Timeout scenario -> 503 + SERVICE_UNAVAILABLE code
 * (3) No JWT -> 401
 * (4) Wrong scope -> 403
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@Testcontainers
class UpstreamFailureReturns503Test {

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

    @Test
    @DisplayName("(1) CB-OPEN (DashboardUnavailableException) -> 503 + Problem-Detail + audit ERROR")
    void circuitBreakerOpen_returns503_andEmitsAuditError() throws Exception {
        // Given: AccountPort throws DashboardUnavailableException (domain exception, simulates CB-open)
        when(accountPort.fetchAccounts(any()))
                .thenThrow(new DashboardUnavailableException("CB open"));
        doNothing().when(auditEventPublisher).publish(any());

        // When
        mockMvc.perform(get("/api/v1/balance-dashboard")
                        .with(jwt().jwt(j -> j.subject(CUSTOMER_ID.toString())
                                .claim("scope", "accounts:read")))
                        .accept(MediaType.APPLICATION_JSON))
                // Then: 503 Service Unavailable
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                // Retry-After header present
                .andExpect(header().exists("Retry-After"))
                // X-Correlation-Id present
                .andExpect(header().exists("X-Correlation-Id"))
                // No stack trace or upstream service name in response body (Security C-2)
                .andExpect(jsonPath("$.detail").doesNotExist().or(
                        jsonPath("$.detail").value("กรุณาลองใหม่อีกครั้งในอีกสักครู่")));

        // Audit ERROR emitted
        ArgumentCaptor<AuditEventRecord> captor = ArgumentCaptor.forClass(AuditEventRecord.class);
        verify(auditEventPublisher, times(1)).publish(captor.capture());
        assertThat(captor.getValue().result()).isEqualTo(Result.ERROR);
    }

    @Test
    @DisplayName("(2) Timeout -> 503 + SERVICE_UNAVAILABLE code")
    void timeout_returns503_withServiceUnavailableCode() throws Exception {
        // Given: Timeout exception wrapped as DashboardUnavailableException (domain exception)
        when(accountPort.fetchAccounts(any()))
                .thenThrow(new DashboardUnavailableException("timeout"));
        doNothing().when(auditEventPublisher).publish(any());

        mockMvc.perform(get("/api/v1/balance-dashboard")
                        .with(jwt().jwt(j -> j.subject(CUSTOMER_ID.toString())
                                .claim("scope", "accounts:read")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("(3) No JWT -> 401")
    void noJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/balance-dashboard")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("(4) Wrong scope -> 403 Forbidden")
    void wrongScope_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/balance-dashboard")
                        .with(jwt().jwt(j -> j.subject(CUSTOMER_ID.toString())
                                .claim("scope", "transfers:write"))) // wrong scope
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
