package com.bank.transfer.domain.exception;

/**
 * Thrown when the source or destination account is frozen and cannot participate
 * in a transfer.
 * Maps to HTTP 422 with code {@code SOURCE_ACCOUNT_FROZEN} or {@code ACCOUNT_FROZEN}.
 */
public final class AccountFrozenException extends DomainException {

    /**
     * Constructs an AccountFrozenException for a specific account role.
     *
     * @param code the specific error code: "SOURCE_ACCOUNT_FROZEN" or "ACCOUNT_FROZEN"
     */
    public AccountFrozenException(final String code) {
        super("The account is frozen and cannot participate in transfers.", code);
    }
}
