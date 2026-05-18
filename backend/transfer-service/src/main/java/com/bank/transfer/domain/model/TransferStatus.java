package com.bank.transfer.domain.model;

/**
 * State machine for a money transfer aggregate.
 *
 * <pre>
 * PENDING -> COMPLETED                                      (happy path)
 * PENDING -> FAILED                                         (rejected pre-debit)
 * PENDING -> COMPENSATION_PENDING -> FAILED_COMPENSATED     (saga rollback ok)
 * PENDING -> COMPENSATION_PENDING -> COMPENSATION_FAILED    (double fault)
 * </pre>
 */
public enum TransferStatus {

    /** Transfer has been accepted and is being processed. */
    PENDING,

    /** Transfer completed successfully — debit and credit both applied. */
    COMPLETED,

    /** Transfer rejected before any money movement (e.g. insufficient funds). */
    FAILED,

    /** Debit succeeded, credit failed; compensation (reversal) is in progress. */
    COMPENSATION_PENDING,

    /** Compensation (reversal of debit) succeeded after credit failure. */
    FAILED_COMPENSATED,

    /** Compensation also failed — requires manual ops intervention. */
    COMPENSATION_FAILED;

    /**
     * Returns true if this status is a terminal (non-retryable) state.
     *
     * @return true for COMPLETED, FAILED, FAILED_COMPENSATED, COMPENSATION_FAILED
     */
    public boolean isTerminal() {
        return switch (this) {
            case COMPLETED, FAILED, FAILED_COMPENSATED, COMPENSATION_FAILED -> true;
            default -> false;
        };
    }
}
