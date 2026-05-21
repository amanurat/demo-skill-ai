package com.bank.balancedashboard.domain.port.in;

import com.bank.balancedashboard.domain.model.RankedDashboard;

import java.util.UUID;

/**
 * Primary (inbound) port — driven by the REST controller.
 *
 * <p>Returns the authenticated customer's eligible accounts ranked by balance DESC.
 * Implemented by {@code BalanceDashboardService} in the application layer.
 *
 * <p>The {@code customerId} MUST be derived from the JWT {@code sub} claim by
 * {@code CustomerIdResolver} before being passed here. The use case trusts the
 * caller to provide a validated UUID.
 *
 * <p>Domain interface — ZERO Spring/Kafka/Redis imports permitted in this package.
 */
public interface LoadDashboardUseCase {

    /**
     * Loads the ranked balance dashboard for the given customer.
     *
     * <p>Orchestration sequence:
     * <ol>
     *   <li>Check CachePort — return cached RankedDashboard if present (X-Cache: HIT)</li>
     *   <li>On miss: call AccountPort.fetchAccounts() (Resilience4j in adapter)</li>
     *   <li>Rank via Ranker, write to CachePort</li>
     *   <li>Emit audit via AuditEventPublisher (BR-014 — never short-circuited)</li>
     *   <li>Return RankedDashboard</li>
     * </ol>
     *
     * @param customerId authenticated customer UUID (from JWT sub — validated by CustomerIdResolver)
     * @return ranked dashboard (may have empty accounts list — AC-001-E1)
     * @throws com.bank.balancedashboard.infrastructure.client.UpstreamUnavailableException
     *         if account-service is unavailable after Resilience4j exhaustion
     */
    RankedDashboard loadDashboard(UUID customerId);
}
