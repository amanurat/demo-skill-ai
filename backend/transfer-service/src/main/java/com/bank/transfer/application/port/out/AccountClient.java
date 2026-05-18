package com.bank.transfer.application.port.out;

import java.util.UUID;

/**
 * Outbound port for communicating with the account-service.
 *
 * <p>In v1 this is fulfilled by {@link com.bank.transfer.infrastructure.client.AccountClientStub},
 * which returns a canned ACTIVE account. A real Feign/WebClient adapter will replace it
 * in US-006 once account-service is deployed.
 */
public interface AccountClient {

    /**
     * Retrieves account information by account UUID.
     *
     * <p>Implementations must apply Resilience4j decorators:
     * time-limiter (800ms), circuit breaker, bulkhead.
     *
     * @param accountId the account UUID to look up
     * @return account info snapshot
     * @throws com.bank.transfer.domain.exception.DependencyUnavailableException
     *         if the account-service is unavailable or the circuit is open
     */
    AccountInfo getAccountInfo(UUID accountId);
}
