package com.bank.transfer.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root for an intra-bank THB money transfer.
 *
 * <p>All business-rule enforcement lives here, not in application services.
 * State transitions are exposed as explicit methods that validate the current
 * state before mutating.
 *
 * <p>Mapped to the {@code transfers} table via JPA; JPA entity is a separate
 * class in the infrastructure layer to preserve domain purity.
 */
public final class Transfer {

    private final UUID transferId;
    // Not final: assigned once post-persist via assignReferenceNumber() by the use case
    private String referenceNumber;
    private final AccountId sourceAccountId;
    private final AccountId destinationAccountId;
    private final UUID initiatorUserId;
    private final UUID initiatorCustomerId;
    private final Money amount;
    private final String memo;
    private final String channel;
    private final String correlationId;
    private TransferStatus status;
    private String failureReason;
    private Instant completedAt;
    private long version;

    /**
     * Private constructor — use the {@link #create} factory method.
     */
    private Transfer(
            final UUID transferId,
            final String referenceNumber,
            final AccountId sourceAccountId,
            final AccountId destinationAccountId,
            final UUID initiatorUserId,
            final UUID initiatorCustomerId,
            final Money amount,
            final String memo,
            final String channel,
            final String correlationId,
            final TransferStatus status,
            final String failureReason,
            final Instant completedAt,
            final long version) {
        this.transferId = transferId;
        this.referenceNumber = referenceNumber;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.initiatorUserId = initiatorUserId;
        this.initiatorCustomerId = initiatorCustomerId;
        this.amount = amount;
        this.memo = memo;
        this.channel = channel;
        this.correlationId = correlationId;
        this.status = status;
        this.failureReason = failureReason;
        this.completedAt = completedAt;
        this.version = version;
    }

    /**
     * Factory method for a brand-new transfer in PENDING state.
     *
     * <p>Validates all invariants at creation time:
     * <ul>
     *   <li>Amount must be positive</li>
     *   <li>Source and destination must differ</li>
     *   <li>Currency must be THB (v1 constraint)</li>
     * </ul>
     *
     * @param sourceAccountId      the account being debited
     * @param destinationAccountId the account being credited
     * @param initiatorUserId      the authenticated user UUID
     * @param initiatorCustomerId  the customer the user belongs to
     * @param amount               the Money to transfer (must be positive, THB)
     * @param memo                 optional free-text memo (max 200 chars)
     * @param channel              origination channel
     * @param correlationId        W3C trace / correlation id
     * @return a new Transfer in PENDING status
     */
    public static Transfer create(
            final AccountId sourceAccountId,
            final AccountId destinationAccountId,
            final UUID initiatorUserId,
            final UUID initiatorCustomerId,
            final Money amount,
            final String memo,
            final String channel,
            final String correlationId) {

        Objects.requireNonNull(sourceAccountId, "sourceAccountId required");
        Objects.requireNonNull(destinationAccountId, "destinationAccountId required");
        Objects.requireNonNull(initiatorUserId, "initiatorUserId required");
        Objects.requireNonNull(initiatorCustomerId, "initiatorCustomerId required");
        Objects.requireNonNull(amount, "amount required");
        Objects.requireNonNull(channel, "channel required");
        Objects.requireNonNull(correlationId, "correlationId required");

        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
        if (!"THB".equals(amount.getCurrency().getCurrencyCode())) {
            throw new IllegalArgumentException("Only THB transfers are supported in v1");
        }
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException("Source and destination accounts must differ");
        }
        if (memo != null && memo.length() > 200) {
            throw new IllegalArgumentException("Memo exceeds 200 characters: " + memo.length());
        }

        return new Transfer(
            UUID.randomUUID(),
            "",                    // reference number assigned post-commit by use case
            sourceAccountId,
            destinationAccountId,
            initiatorUserId,
            initiatorCustomerId,
            amount,
            memo,
            channel,
            correlationId,
            TransferStatus.PENDING,
            null,
            null,
            0L
        );
    }

    /**
     * Reconstitutes a Transfer from persistent storage (used by JPA adapter).
     * No domain-rule validation — assumes the DB is the source of truth.
     *
     * @param transferId           persisted UUID
     * @param referenceNumber      assigned reference
     * @param sourceAccountId      source account VO
     * @param destinationAccountId destination account VO
     * @param initiatorUserId      user UUID
     * @param initiatorCustomerId  customer UUID
     * @param amount               Money VO
     * @param memo                 optional memo
     * @param channel              origination channel
     * @param correlationId        trace id
     * @param status               current status
     * @param failureReason        nullable reason code
     * @param completedAt          nullable terminal timestamp
     * @param version              optimistic lock version
     * @return reconstituted Transfer
     */
    public static Transfer reconstitute(
            final UUID transferId,
            final String referenceNumber,
            final AccountId sourceAccountId,
            final AccountId destinationAccountId,
            final UUID initiatorUserId,
            final UUID initiatorCustomerId,
            final Money amount,
            final String memo,
            final String channel,
            final String correlationId,
            final TransferStatus status,
            final String failureReason,
            final Instant completedAt,
            final long version) {
        return new Transfer(
            transferId, referenceNumber, sourceAccountId, destinationAccountId,
            initiatorUserId, initiatorCustomerId, amount, memo, channel, correlationId,
            status, failureReason, completedAt, version);
    }

    /**
     * Transitions this transfer to COMPLETED.
     *
     * @throws IllegalStateException if not currently PENDING
     */
    public void markCompleted() {
        requireStatus(TransferStatus.PENDING, "markCompleted");
        this.status = TransferStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    /**
     * Transitions this transfer to FAILED.
     *
     * @param reason a machine-readable reason code (e.g. "INSUFFICIENT_FUNDS")
     * @throws IllegalStateException if not currently PENDING
     */
    public void markFailed(final String reason) {
        requireStatus(TransferStatus.PENDING, "markFailed");
        Objects.requireNonNull(reason, "failureReason required");
        this.status = TransferStatus.FAILED;
        this.failureReason = reason;
        this.completedAt = Instant.now();
    }

    /**
     * Transitions to COMPENSATION_PENDING (debit succeeded, credit failed).
     *
     * @throws IllegalStateException if not currently PENDING
     */
    public void markCompensationPending() {
        requireStatus(TransferStatus.PENDING, "markCompensationPending");
        this.status = TransferStatus.COMPENSATION_PENDING;
    }

    /**
     * Transitions to FAILED_COMPENSATED (reversal succeeded).
     *
     * @throws IllegalStateException if not currently COMPENSATION_PENDING
     */
    public void markCompensated() {
        requireStatus(TransferStatus.COMPENSATION_PENDING, "markCompensated");
        this.status = TransferStatus.FAILED_COMPENSATED;
        this.completedAt = Instant.now();
    }

    /**
     * Transitions to COMPENSATION_FAILED (reversal also failed — ops alert required).
     *
     * @throws IllegalStateException if not currently COMPENSATION_PENDING
     */
    public void markCompensationFailed() {
        requireStatus(TransferStatus.COMPENSATION_PENDING, "markCompensationFailed");
        this.status = TransferStatus.COMPENSATION_FAILED;
        this.completedAt = Instant.now();
    }

    /**
     * Assigns the server-generated reference number (called once, after initial persist).
     *
     * @param referenceNumber the generated reference (e.g. "TRF-20260518-0000001A")
     * @throws IllegalStateException if a reference is already assigned
     */
    public void assignReferenceNumber(final String referenceNumber) {
        if (this.referenceNumber != null && !this.referenceNumber.isBlank()) {
            throw new IllegalStateException(
                "Reference number already assigned: " + this.referenceNumber);
        }
        this.referenceNumber = Objects.requireNonNull(referenceNumber, "referenceNumber required");
    }

    // --- Getters ---

    public UUID getTransferId() { return transferId; }
    public String getReferenceNumber() { return referenceNumber; }
    public AccountId getSourceAccountId() { return sourceAccountId; }
    public AccountId getDestinationAccountId() { return destinationAccountId; }
    public UUID getInitiatorUserId() { return initiatorUserId; }
    public UUID getInitiatorCustomerId() { return initiatorCustomerId; }
    public Money getAmount() { return amount; }
    public String getMemo() { return memo; }
    public String getChannel() { return channel; }
    public String getCorrelationId() { return correlationId; }
    public TransferStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Instant getCompletedAt() { return completedAt; }
    public long getVersion() { return version; }

    // --- Private helpers ---

    private void requireStatus(final TransferStatus expected, final String operation) {
        if (this.status != expected) {
            throw new IllegalStateException(
                "Cannot perform '" + operation + "' when status is " + this.status
                + " (expected " + expected + ")");
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Transfer t)) {
            return false;
        }
        return Objects.equals(transferId, t.transferId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transferId);
    }

    @Override
    public String toString() {
        // Safe: only non-PII fields
        return "Transfer{id=" + transferId + ", ref=" + referenceNumber
            + ", status=" + status + ", amount=" + amount.toWireString()
            + " " + amount.getCurrency() + "}";
    }
}
