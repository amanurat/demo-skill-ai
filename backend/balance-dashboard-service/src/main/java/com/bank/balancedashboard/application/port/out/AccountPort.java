package com.bank.balancedashboard.application.port.out;

import com.bank.balancedashboard.domain.exception.DashboardUnavailableException;
import com.bank.balancedashboard.domain.model.AccountView;

import java.util.List;
import java.util.UUID;

/**
 * Secondary (outbound) port for fetching eligible accounts from account-service.
 * Implemented by {@code AccountClientAdapter} in the infrastructure layer.
 *
 * <p>Contract:
 * <ul>
 *   <li>Returns already mapped and eligibility-filtered AccountView list.
 *       EligibilityPolicy is applied inside the adapter, not here.</li>
 *   <li>Returns empty list (never null) when customer has zero eligible accounts.</li>
 *   <li>Throws {@link DashboardUnavailableException} (unchecked) on any Resilience4j
 *       failure: TimeoutException, CallNotPermittedException, BulkheadFullException,
 *       or HTTP 5xx after all retries.</li>
 *   <li>Does NOT throw on HTTP 4xx (those are ignoreExceptions in Resilience4j config).</li>
 * </ul>
 */
public interface AccountPort {

    /**
     * Fetches and filters all eligible accounts for the given customer.
     *
     * @param customerId authenticated customer UUID (JWT sub — already validated)
     * @return list of eligible ranked-ready AccountViews (may be empty, never null)
     * @throws DashboardUnavailableException
     *         if account-service is unreachable or returns 5xx after retries
     */
    List<AccountView> fetchAccounts(UUID customerId);
}
