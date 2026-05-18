package com.bank.transfer.application.port.out;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Immutable snapshot of account information returned by the account-service client.
 *
 * <p>Only the fields needed by the transfer saga are included. PII is omitted
 * from this VO — it is never logged directly.
 *
 * @param accountId        the account UUID
 * @param customerId       the owning customer UUID
 * @param status           account status (ACTIVE, FROZEN, INACTIVE)
 * @param availableBalance current spendable balance
 * @param currency         ISO 4217 currency of the account
 */
public record AccountInfo(
    UUID accountId,
    UUID customerId,
    String status,
    BigDecimal availableBalance,
    String currency
) {

    /** Account is open and transactable. */
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    /** Account is frozen — no debits or credits allowed. */
    public boolean isFrozen() {
        return "FROZEN".equals(status);
    }
}
