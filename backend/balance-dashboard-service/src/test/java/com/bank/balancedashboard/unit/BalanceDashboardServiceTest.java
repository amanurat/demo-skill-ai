package com.bank.balancedashboard.unit;

import com.bank.balancedashboard.application.port.out.AccountPort;
import com.bank.balancedashboard.application.port.out.AuditEventPublisher;
import com.bank.balancedashboard.application.port.out.CachePort;
import com.bank.balancedashboard.application.service.BalanceDashboardService;
import com.bank.balancedashboard.domain.audit.AuditEventRecord;
import com.bank.balancedashboard.domain.audit.Result;
import com.bank.balancedashboard.domain.model.AccountType;
import com.bank.balancedashboard.domain.model.AccountView;
import com.bank.balancedashboard.domain.model.RankedDashboard;
import com.bank.balancedashboard.domain.policy.Ranker;
import com.bank.balancedashboard.infrastructure.client.UpstreamUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BalanceDashboardService} — all 8 test scenarios from task-plan §Layer 3.
 * All ports mocked via Mockito. ZERO Spring context.
 * Coverage target: >= 95% (critical path).
 */
@ExtendWith(MockitoExtension.class)
class BalanceDashboardServiceTest {

    @Mock private AccountPort accountPort;
    @Mock private CachePort cachePort;
    @Mock private AuditEventPublisher auditEventPublisher;

    private Ranker ranker;
    private BalanceDashboardService service;
    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @BeforeEach
    void setUp() {
        ranker = new Ranker();
        service = new BalanceDashboardService(accountPort, cachePort, auditEventPublisher, ranker);
    }

    @Test
    @DisplayName("(1) cacheHit_returnsSnapshot_emitsAudit — AccountPort never called")
    void cacheHit_returnsSnapshot_emitsAudit() {
        // Given: cache returns populated dashboard
        RankedDashboard cached = rankedDashboard(List.of(accountView("100.00")), true);
        when(cachePort.get(CUSTOMER_ID)).thenReturn(Optional.of(cached));

        // When
        RankedDashboard result = service.loadDashboard(CUSTOMER_ID);

        // Then: returns from cache
        assertThat(result.cacheHit()).isTrue();
        assertThat(result.freshness()).isEqualTo("snapshot");

        // AccountPort NEVER called on cache hit
        verify(accountPort, never()).fetchAccounts(any());

        // Audit emitted ONCE with cacheHit=true (BR-014)
        ArgumentCaptor<AuditEventRecord> auditCaptor = ArgumentCaptor.forClass(AuditEventRecord.class);
        verify(auditEventPublisher, times(1)).publish(auditCaptor.capture());
        assertThat(auditCaptor.getValue().result()).isEqualTo(Result.SUCCESS);
        assertThat(auditCaptor.getValue().cacheHit()).isTrue();
    }

    @Test
    @DisplayName("(2) cacheMiss_fetchesAndRanks_writesCache_emitsAudit")
    void cacheMiss_fetchesAndRanks_writesCache_emitsAudit() {
        // Given: cache miss, AccountPort returns 3 accounts
        when(cachePort.get(CUSTOMER_ID)).thenReturn(Optional.empty());
        List<AccountView> accounts = List.of(
                accountView("300.00"), accountView("100.00"), accountView("200.00"));
        when(accountPort.fetchAccounts(CUSTOMER_ID)).thenReturn(accounts);

        // When
        RankedDashboard result = service.loadDashboard(CUSTOMER_ID);

        // Then: returns ranked (300 > 200 > 100)
        assertThat(result.accounts()).hasSize(3);
        assertThat(result.accounts().get(0).balance()).isEqualByComparingTo("300.00");
        assertThat(result.accounts().get(1).balance()).isEqualByComparingTo("200.00");
        assertThat(result.cacheHit()).isFalse();
        assertThat(result.freshness()).isEqualTo("live");

        // Cache written once
        verify(cachePort, times(1)).put(eq(CUSTOMER_ID), any());

        // Audit emitted with cacheHit=false
        ArgumentCaptor<AuditEventRecord> auditCaptor = ArgumentCaptor.forClass(AuditEventRecord.class);
        verify(auditEventPublisher, times(1)).publish(auditCaptor.capture());
        assertThat(auditCaptor.getValue().result()).isEqualTo(Result.SUCCESS);
        assertThat(auditCaptor.getValue().cacheHit()).isFalse();
    }

    @Test
    @DisplayName("(3) emptyAccounts_returns200WithEmptyArray_emitsAuditAccountCount0")
    void emptyAccounts_returns200WithEmptyArray_emitsAuditAccountCount0() {
        // Given: cache miss, AccountPort returns empty list (AC-001-E1)
        when(cachePort.get(CUSTOMER_ID)).thenReturn(Optional.empty());
        when(accountPort.fetchAccounts(CUSTOMER_ID)).thenReturn(List.of());

        // When
        RankedDashboard result = service.loadDashboard(CUSTOMER_ID);

        // Then: empty accounts, still 200
        assertThat(result.accounts()).isEmpty();

        // Audit with accountCount=0
        ArgumentCaptor<AuditEventRecord> auditCaptor = ArgumentCaptor.forClass(AuditEventRecord.class);
        verify(auditEventPublisher, times(1)).publish(auditCaptor.capture());
        assertThat(auditCaptor.getValue().result()).isEqualTo(Result.SUCCESS);
        assertThat(auditCaptor.getValue().accountCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("(4) upstreamFailure_returns503_emitsAuditError")
    void upstreamFailure_returns503_emitsAuditError() {
        // Given: cache miss, AccountPort throws UpstreamUnavailableException
        when(cachePort.get(CUSTOMER_ID)).thenReturn(Optional.empty());
        when(accountPort.fetchAccounts(CUSTOMER_ID))
                .thenThrow(new UpstreamUnavailableException("timeout", "TIMEOUT"));

        // When / Then: UpstreamUnavailableException re-thrown
        assertThatThrownBy(() -> service.loadDashboard(CUSTOMER_ID))
                .isInstanceOf(UpstreamUnavailableException.class);

        // CachePort.put() NEVER called on failure
        verify(cachePort, never()).put(any(), any());

        // Audit ERROR emitted
        ArgumentCaptor<AuditEventRecord> auditCaptor = ArgumentCaptor.forClass(AuditEventRecord.class);
        verify(auditEventPublisher, times(1)).publish(auditCaptor.capture());
        assertThat(auditCaptor.getValue().result()).isEqualTo(Result.ERROR);
    }

    @Test
    @DisplayName("(5) redisFailure_failOpen_fetchesAccountClient_emitsAudit")
    void redisFailure_failOpen_fetchesAccountClient_emitsAudit() {
        // Given: CachePort.get() throws RuntimeException (Redis unavailable)
        when(cachePort.get(CUSTOMER_ID)).thenThrow(new RuntimeException("Redis connection refused"));
        List<AccountView> accounts = List.of(accountView("150.00"), accountView("75.00"));
        when(accountPort.fetchAccounts(CUSTOMER_ID)).thenReturn(accounts);

        // When: service should fail-open (not fail-closed)
        RankedDashboard result = service.loadDashboard(CUSTOMER_ID);

        // Then: returns ranked result from AccountClient (fail-open, BR-015)
        assertThat(result.accounts()).hasSize(2);
        assertThat(result.cacheHit()).isFalse();
        assertThat(result.freshness()).isEqualTo("live");

        // Audit SUCCESS emitted
        ArgumentCaptor<AuditEventRecord> auditCaptor = ArgumentCaptor.forClass(AuditEventRecord.class);
        verify(auditEventPublisher, times(1)).publish(auditCaptor.capture());
        assertThat(auditCaptor.getValue().result()).isEqualTo(Result.SUCCESS);
        assertThat(auditCaptor.getValue().cacheHit()).isFalse();
    }

    @Test
    @DisplayName("(6) auditAlwaysEmitted_evenOnCacheHit_BR014")
    void auditAlwaysEmitted_evenOnCacheHit_BR014() {
        // Given: cache hit
        RankedDashboard cached = rankedDashboard(List.of(accountView("50.00")), true);
        when(cachePort.get(CUSTOMER_ID)).thenReturn(Optional.of(cached));

        // When
        service.loadDashboard(CUSTOMER_ID);

        // Then: audit emitted regardless of cache hit (BR-014 — cache NEVER short-circuits audit)
        verify(auditEventPublisher, times(1)).publish(any(AuditEventRecord.class));
    }

    @Test
    @DisplayName("(7) accountCountInAudit_matchesResponseLength")
    void accountCountInAudit_matchesResponseLength() {
        // Given: AccountPort returns 5 accounts
        when(cachePort.get(CUSTOMER_ID)).thenReturn(Optional.empty());
        List<AccountView> fiveAccounts = List.of(
                accountView("500.00"), accountView("400.00"), accountView("300.00"),
                accountView("200.00"), accountView("100.00"));
        when(accountPort.fetchAccounts(CUSTOMER_ID)).thenReturn(fiveAccounts);

        // When
        service.loadDashboard(CUSTOMER_ID);

        // Then: audit.accountCount == 5
        ArgumentCaptor<AuditEventRecord> auditCaptor = ArgumentCaptor.forClass(AuditEventRecord.class);
        verify(auditEventPublisher, times(1)).publish(auditCaptor.capture());
        assertThat(auditCaptor.getValue().accountCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("(8) correlationIdPassedToAudit")
    void correlationIdPassedToAudit() {
        // Given: any happy path
        when(cachePort.get(CUSTOMER_ID)).thenReturn(Optional.empty());
        when(accountPort.fetchAccounts(CUSTOMER_ID)).thenReturn(List.of(accountView("200.00")));

        // When
        RankedDashboard result = service.loadDashboard(CUSTOMER_ID);

        // Then: audit correlationId matches response correlationId
        ArgumentCaptor<AuditEventRecord> auditCaptor = ArgumentCaptor.forClass(AuditEventRecord.class);
        verify(auditEventPublisher, times(1)).publish(auditCaptor.capture());
        String auditCorrelationId = auditCaptor.getValue().correlationId();
        assertThat(auditCorrelationId).isNotNull().isNotBlank();
        // The correlationId in the response matches the one in the audit record
        assertThat(result.correlationId()).isEqualTo(auditCorrelationId);
    }

    // ===== Helpers =====

    private AccountView accountView(String balance) {
        return new AccountView(
                1,
                UUID.randomUUID(),
                "****1234",
                AccountType.SAVINGS,
                new BigDecimal(balance),
                "THB",
                Instant.now(),
                false,
                "account.type.savings"
        );
    }

    private RankedDashboard rankedDashboard(List<AccountView> accounts, boolean cacheHit) {
        return new RankedDashboard(accounts, cacheHit ? "snapshot" : "live", cacheHit,
                UUID.randomUUID().toString());
    }
}
