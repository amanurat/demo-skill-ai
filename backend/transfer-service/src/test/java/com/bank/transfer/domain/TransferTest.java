package com.bank.transfer.domain;

import com.bank.transfer.domain.model.AccountId;
import com.bank.transfer.domain.model.Money;
import com.bank.transfer.domain.model.Transfer;
import com.bank.transfer.domain.model.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link Transfer} domain aggregate.
 *
 * <p>Covers the full state machine, reference number assignment, and all
 * invariant validations at construction time.
 */
@DisplayName("Transfer domain aggregate")
class TransferTest {

    private static final UUID SRC_ID  = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEST_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CUST_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Money AMOUNT = Money.of("1500.0000", "THB");

    private Transfer pendingTransfer;

    @BeforeEach
    void setUp() {
        pendingTransfer = Transfer.create(
            new AccountId(SRC_ID),
            new AccountId(DEST_ID),
            USER_ID,
            CUST_ID,
            AMOUNT,
            "Rent May 2026",
            "INTERNET_BANKING",
            "correlation-001"
        );
    }

    // --- Factory validation ---

    @Test
    @DisplayName("should_create_transfer_in_PENDING_status")
    void should_create_transfer_in_PENDING_status() {
        assertThat(pendingTransfer.getStatus()).isEqualTo(TransferStatus.PENDING);
        assertThat(pendingTransfer.getTransferId()).isNotNull();
        assertThat(pendingTransfer.getCompletedAt()).isNull();
        assertThat(pendingTransfer.getFailureReason()).isNull();
        assertThat(pendingTransfer.getVersion()).isEqualTo(0L);
    }

    @Test
    @DisplayName("should_reject_zero_amount_at_creation")
    void should_reject_zero_amount_at_creation() {
        assertThatThrownBy(() -> Transfer.create(
            new AccountId(SRC_ID), new AccountId(DEST_ID),
            USER_ID, CUST_ID,
            Money.of(BigDecimal.ZERO, "THB"),
            null, "INTERNET_BANKING", "corr-1"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("positive");
    }

    @Test
    @DisplayName("should_reject_non_THB_currency_at_creation")
    void should_reject_non_THB_currency_at_creation() {
        assertThatThrownBy(() -> Transfer.create(
            new AccountId(SRC_ID), new AccountId(DEST_ID),
            USER_ID, CUST_ID,
            Money.of(new BigDecimal("100"), "USD"),
            null, "INTERNET_BANKING", "corr-2"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("THB");
    }

    @Test
    @DisplayName("should_reject_same_source_and_destination_account")
    void should_reject_same_source_and_destination_account() {
        assertThatThrownBy(() -> Transfer.create(
            new AccountId(SRC_ID), new AccountId(SRC_ID),
            USER_ID, CUST_ID,
            AMOUNT, null, "INTERNET_BANKING", "corr-3"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("differ");
    }

    @Test
    @DisplayName("should_reject_memo_exceeding_200_characters")
    void should_reject_memo_exceeding_200_characters() {
        String longMemo = "x".repeat(201);
        assertThatThrownBy(() -> Transfer.create(
            new AccountId(SRC_ID), new AccountId(DEST_ID),
            USER_ID, CUST_ID,
            AMOUNT, longMemo, "INTERNET_BANKING", "corr-4"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Memo exceeds");
    }

    // --- State machine: happy path ---

    @Test
    @DisplayName("should_transition_to_COMPLETED_from_PENDING")
    void should_transition_to_COMPLETED_from_PENDING() {
        pendingTransfer.markCompleted();
        assertThat(pendingTransfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(pendingTransfer.getCompletedAt()).isNotNull();
        assertThat(TransferStatus.COMPLETED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("should_transition_to_FAILED_from_PENDING")
    void should_transition_to_FAILED_from_PENDING() {
        pendingTransfer.markFailed("INSUFFICIENT_FUNDS");
        assertThat(pendingTransfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(pendingTransfer.getFailureReason()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(pendingTransfer.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("should_transition_through_saga_rollback_path")
    void should_transition_through_saga_rollback_path() {
        pendingTransfer.markCompensationPending();
        assertThat(pendingTransfer.getStatus()).isEqualTo(TransferStatus.COMPENSATION_PENDING);

        pendingTransfer.markCompensated();
        assertThat(pendingTransfer.getStatus()).isEqualTo(TransferStatus.FAILED_COMPENSATED);
        assertThat(pendingTransfer.getCompletedAt()).isNotNull();
        assertThat(TransferStatus.FAILED_COMPENSATED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("should_transition_to_COMPENSATION_FAILED_when_reversal_also_fails")
    void should_transition_to_COMPENSATION_FAILED_when_reversal_also_fails() {
        pendingTransfer.markCompensationPending();
        pendingTransfer.markCompensationFailed();
        assertThat(pendingTransfer.getStatus()).isEqualTo(TransferStatus.COMPENSATION_FAILED);
        assertThat(TransferStatus.COMPENSATION_FAILED.isTerminal()).isTrue();
    }

    // --- Invalid state transitions ---

    @Test
    @DisplayName("should_reject_markCompleted_when_already_COMPLETED")
    void should_reject_markCompleted_when_already_COMPLETED() {
        pendingTransfer.markCompleted();
        assertThatThrownBy(pendingTransfer::markCompleted)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("markCompleted");
    }

    @Test
    @DisplayName("should_reject_markFailed_when_not_PENDING")
    void should_reject_markFailed_when_not_PENDING() {
        pendingTransfer.markCompleted();
        assertThatThrownBy(() -> pendingTransfer.markFailed("some reason"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("should_reject_markCompensated_when_not_COMPENSATION_PENDING")
    void should_reject_markCompensated_when_not_COMPENSATION_PENDING() {
        // Still PENDING — cannot jump to compensated
        assertThatThrownBy(pendingTransfer::markCompensated)
            .isInstanceOf(IllegalStateException.class);
    }

    // --- Reference number ---

    @Test
    @DisplayName("should_assign_reference_number_once")
    void should_assign_reference_number_once() {
        pendingTransfer.assignReferenceNumber("TRF-20260518-ABCD1234");
        assertThat(pendingTransfer.getReferenceNumber()).isEqualTo("TRF-20260518-ABCD1234");
    }

    @Test
    @DisplayName("should_reject_second_reference_number_assignment")
    void should_reject_second_reference_number_assignment() {
        pendingTransfer.assignReferenceNumber("TRF-20260518-ABCD1234");
        assertThatThrownBy(() -> pendingTransfer.assignReferenceNumber("TRF-20260518-EEEE9999"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already assigned");
    }

    // --- Optimistic version ---

    @Test
    @DisplayName("should_expose_version_for_optimistic_locking")
    void should_expose_version_for_optimistic_locking() {
        // New aggregate starts at version 0
        assertThat(pendingTransfer.getVersion()).isEqualTo(0L);
    }

    // --- Equality ---

    @Test
    @DisplayName("should_be_equal_by_transferId")
    void should_be_equal_by_transferId() {
        // Reconstitute a copy with the same UUID
        Transfer copy = Transfer.reconstitute(
            pendingTransfer.getTransferId(),
            "REF", new AccountId(SRC_ID), new AccountId(DEST_ID),
            USER_ID, CUST_ID, AMOUNT, null, "INTERNET_BANKING", "c", TransferStatus.PENDING,
            null, null, 0L
        );
        assertThat(pendingTransfer).isEqualTo(copy);
        assertThat(pendingTransfer.hashCode()).isEqualTo(copy.hashCode());
    }

    @Test
    @DisplayName("should_not_be_equal_to_different_transferId")
    void should_not_be_equal_to_different_transferId() {
        Transfer other = Transfer.create(
            new AccountId(SRC_ID), new AccountId(DEST_ID),
            USER_ID, CUST_ID, AMOUNT, null, "INTERNET_BANKING", "c"
        );
        assertThat(pendingTransfer).isNotEqualTo(other);
    }

    // --- Safe toString (no PII) ---

    @Test
    @DisplayName("should_not_include_full_account_ids_in_toString")
    void should_not_include_full_account_ids_in_toString() {
        String str = pendingTransfer.toString();
        // Transfer.toString() should not log full UUIDs — only partial via AccountId.toString()
        assertThat(str).doesNotContain("11111111-1111-1111-1111-111111111111");
    }
}
