package com.bank.transfer.infrastructure.client;

import com.bank.transfer.application.port.out.AccountClient;
import com.bank.transfer.application.port.out.AccountInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Stub implementation of {@link AccountClient}.
 *
 * <p>Returns a canned ACTIVE account with 10,000,000 THB available balance for
 * any account UUID. This allows the transfer saga to run end-to-end without a
 * real account-service in v1 (US-001 + US-003 scope).
 *
 * <p>TODO (US-006): Replace with a Feign/WebClient adapter decorated with
 * Resilience4j circuit-breaker, bulkhead, and time-limiter (800ms).
 */
@Component
public class AccountClientStub implements AccountClient {

    private static final Logger log = LoggerFactory.getLogger(AccountClientStub.class);
    private static final BigDecimal STUB_BALANCE = new BigDecimal("10000000.0000");
    private static final String STUB_CURRENCY = "THB";
    private static final String STUB_STATUS = "ACTIVE";

    /** Canned customer UUID returned for all accounts in stub mode. */
    private static final UUID STUB_CUSTOMER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    /**
     * {@inheritDoc}
     *
     * <p>Always returns an ACTIVE account with 10M THB balance.
     * Real Feign client will be injected in US-006.
     */
    @Override
    public AccountInfo getAccountInfo(final UUID accountId) {
        log.debug("account_client.stub.called account_id_suffix={}",
            accountId.toString().substring(accountId.toString().length() - 4));
        return new AccountInfo(
            accountId,
            STUB_CUSTOMER_ID,
            STUB_STATUS,
            STUB_BALANCE,
            STUB_CURRENCY
        );
    }
}
