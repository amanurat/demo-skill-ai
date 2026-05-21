package com.bank.account.client;

import java.util.List;
import java.util.UUID;

/**
 * Account client interface from common-libs/account-client-lib.
 *
 * <p>Per account-service-extension.openapi.yaml operationId: listAccountsByCustomer.
 * Returns ALL accounts for a customer regardless of status/type (filtering is the caller's
 * responsibility — AccountClientAdapter applies EligibilityPolicy).
 *
 * <p>The new method {@code listAccountsByCustomer} was added per SUBDEC-002 to support
 * balance-dashboard-service (BDS). See implementation-notes §7.
 */
public interface AccountClientLib {

    /**
     * Lists all accounts owned by a customer (GET /api/v1/accounts?customerId={uuid}).
     *
     * <p>Returns ALL accounts regardless of status or type.
     * Caller (AccountClientAdapter) applies EligibilityPolicy to filter to eligible accounts.
     *
     * @param customerId customer UUID (must equal JWT sub in the forwarded request)
     * @return list of all AccountInfo for the customer (may be empty, never null)
     */
    List<AccountInfo> listAccountsByCustomer(UUID customerId);
}
