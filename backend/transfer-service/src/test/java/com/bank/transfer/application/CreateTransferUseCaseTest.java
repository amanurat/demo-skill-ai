package com.bank.transfer.application;

import com.bank.transfer.application.port.in.CreateTransferCommand;
import com.bank.transfer.application.port.in.TransferResult;
import com.bank.transfer.application.port.in.TransferResult.IdempotencyStatus;
import com.bank.transfer.application.port.out.AccountClient;
import com.bank.transfer.application.port.out.AccountInfo;
import com.bank.transfer.application.port.out.EventPublisher;
import com.bank.transfer.application.port.out.IdempotencyRecord;
import com.bank.transfer.application.port.out.IdempotencyRepository;
import com.bank.transfer.application.port.out.TransferRepository;
import com.bank.transfer.application.saga.TransferSaga;
import com.bank.transfer.application.usecase.CreateTransferUseCase;
import com.bank.transfer.domain.exception.IdempotencyKeyConflictException;
import com.bank.transfer.domain.exception.InsufficientFundsException;
import com.bank.transfer.domain.model.AccountId;
import com.bank.transfer.domain.model.Money;
import com.bank.transfer.domain.model.Transfer;
import com.bank.transfer.domain.model.TransferStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CreateTransferUseCase}.
 *
 * <p>All port dependencies are mocked with Mockito. No Spring context is loaded.
 * Tests focus on the use-case orchestration logic:
 * <ul>
 *   <li>Happy path: new transfer executes saga, writes outbox, returns FIRST_WRITE</li>
 *   <li>Idempotency replay: same key+checksum returns cached result, no saga execution</li>
 *   <li>Idempotency conflict: same key, different checksum → 409 exception</li>
 *   <li>Insufficient funds: saga marks FAILED, use-case returns failed result</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CreateTransferUseCase")
class CreateTransferUseCaseTest {

    private static final UUID SRC_ID  = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DEST_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CUST_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String IDEMPOTENCY_KEY = UUID.randomUUID().toString();

    @Mock private TransferRepository transferRepository;
    @Mock private IdempotencyRepository idempotencyRepository;
    @Mock private AccountClient accountClient;
    @Mock private EventPublisher eventPublisher;
    @Mock private TransferSaga transferSaga;

    private CreateTransferUseCase useCase;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        useCase = new CreateTransferUseCase(
            transferRepository,
            idempotencyRepository,
            accountClient,
            eventPublisher,
            transferSaga,
            objectMapper
        );
    }

    // --- Happy path ---

    @Test
    @DisplayName("should_create_transfer_and_return_FIRST_WRITE_when_new_idempotency_key")
    void should_create_transfer_and_return_FIRST_WRITE_when_new_idempotency_key() {
        // Arrange: no existing idempotency record
        when(idempotencyRepository.findValid(anyString(), eq(CUST_ID)))
            .thenReturn(Optional.empty());

        AccountInfo activeAccount = activeAccountWith10MTHB();
        when(accountClient.getAccountInfo(any())).thenReturn(activeAccount);

        Transfer pending = buildPendingTransfer();
        // Saga completes the transfer
        Transfer completed = buildCompletedTransfer(pending.getTransferId(),
            pending.getReferenceNumber());
        // First save() persists PENDING; second save() persists COMPLETED
        when(transferRepository.save(any())).thenReturn(pending).thenReturn(completed);
        when(transferSaga.execute(any())).thenReturn(completed);

        // Act
        TransferResult result = useCase.execute(buildCommand());

        // Assert
        assertThat(result.idempotencyStatus()).isEqualTo(IdempotencyStatus.FIRST_WRITE);
        assertThat(result.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(result.transferId()).isNotNull();

        // Verify outbox event was published
        verify(eventPublisher).publishTransferRequested(any());

        // Verify idempotency was stored
        verify(idempotencyRepository).insertPending(
            anyString(), eq(CUST_ID), anyString(), anyString(), any(Duration.class));
        verify(idempotencyRepository).updateResult(
            anyString(), eq(CUST_ID), any(), anyString(), anyInt(), anyString());
    }

    // --- Idempotency replay ---

    @Test
    @DisplayName("should_return_cached_response_with_IDEMPOTENT_REPLAY_when_key_replayed")
    void should_return_cached_response_with_IDEMPOTENT_REPLAY_when_key_replayed() throws Exception {
        // Build a pre-serialized cached body matching what the use-case would produce
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        UUID cachedTransferId = UUID.randomUUID();
        // Build a minimal JSON body representing a cached TransferResult
        String cachedBody = buildCachedBody(mapper, cachedTransferId);

        // The checksum of the command must match the stored checksum
        // We need to pre-compute what the use-case would hash; instead we mock findValid
        // to return a record whose checksum MATCHES the actual command checksum.
        // The use-case computes sha256(canonicalize(command)); we let Mockito return a
        // record with a checksum that will equal the real computed value.
        // Strategy: compute the real checksum here as well.
        String realChecksum = computeChecksum(buildCommand());

        IdempotencyRecord record = new IdempotencyRecord(
            "any-key-hash",
            CUST_ID,
            cachedTransferId,
            realChecksum,
            "COMPLETED",
            200,
            cachedBody,
            Instant.now().plus(Duration.ofHours(23))
        );
        when(idempotencyRepository.findValid(anyString(), eq(CUST_ID)))
            .thenReturn(Optional.of(record));

        // Act
        TransferResult result = useCase.execute(buildCommand());

        // Assert
        assertThat(result.idempotencyStatus()).isEqualTo(IdempotencyStatus.IDEMPOTENT_REPLAY);
        assertThat(result.transferId()).isEqualTo(cachedTransferId);

        // Saga must NOT be called on replay
        verify(transferSaga, never()).execute(any());
        verify(accountClient, never()).getAccountInfo(any());
    }

    // --- Idempotency conflict ---

    @Test
    @DisplayName("should_throw_IdempotencyKeyConflictException_when_same_key_has_different_payload")
    void should_throw_IdempotencyKeyConflictException_when_same_key_has_different_payload() {
        // Return a record with a DIFFERENT checksum than the current command would produce
        IdempotencyRecord conflictRecord = new IdempotencyRecord(
            "any-key-hash",
            CUST_ID,
            UUID.randomUUID(),
            "completely-different-checksum-hex-value-not-matching-command",
            "COMPLETED",
            200,
            "{}",
            Instant.now().plus(Duration.ofHours(23))
        );
        when(idempotencyRepository.findValid(anyString(), eq(CUST_ID)))
            .thenReturn(Optional.of(conflictRecord));

        // Act + Assert
        assertThatThrownBy(() -> useCase.execute(buildCommand()))
            .isInstanceOf(IdempotencyKeyConflictException.class)
            .satisfies(e -> assertThat(((IdempotencyKeyConflictException) e).getErrorCode())
                .isEqualTo("IDEMPOTENCY_KEY_CONFLICT"));

        verify(transferSaga, never()).execute(any());
    }

    // --- Insufficient funds ---

    @Test
    @DisplayName("should_return_FAILED_result_when_saga_detects_insufficient_funds")
    void should_return_FAILED_result_when_saga_detects_insufficient_funds() {
        when(idempotencyRepository.findValid(anyString(), eq(CUST_ID)))
            .thenReturn(Optional.empty());

        AccountInfo activeAccount = activeAccountWith10MTHB();
        when(accountClient.getAccountInfo(any())).thenReturn(activeAccount);

        Transfer pending = buildPendingTransfer();
        // Saga returns a FAILED transfer (insufficient funds detected)
        Transfer failed = buildFailedTransfer(pending.getTransferId(),
            pending.getReferenceNumber(), InsufficientFundsException.CODE);
        // First save() persists PENDING; second save() persists FAILED
        when(transferRepository.save(any())).thenReturn(pending).thenReturn(failed);
        when(transferSaga.execute(any())).thenReturn(failed);

        // Act
        TransferResult result = useCase.execute(buildCommand());

        // Assert
        assertThat(result.status()).isEqualTo(TransferStatus.FAILED);
        assertThat(result.failureReason()).isEqualTo(InsufficientFundsException.CODE);
        assertThat(result.idempotencyStatus()).isEqualTo(IdempotencyStatus.FIRST_WRITE);
    }

    // --- Helper builders ---

    private CreateTransferCommand buildCommand() {
        return new CreateTransferCommand(
            SRC_ID,
            DEST_ID,
            new BigDecimal("1500.0000"),
            "THB",
            "Rent May 2026",
            "INTERNET_BANKING",
            IDEMPOTENCY_KEY,
            CUST_ID,
            "test-correlation-001"
        );
    }

    private AccountInfo activeAccountWith10MTHB() {
        return new AccountInfo(
            SRC_ID,
            CUST_ID,
            "ACTIVE",
            new BigDecimal("10000000.0000"),
            "THB"
        );
    }

    private Transfer buildPendingTransfer() {
        Transfer t = Transfer.create(
            new AccountId(SRC_ID),
            new AccountId(DEST_ID),
            CUST_ID,
            CUST_ID,
            Money.of("1500.0000", "THB"),
            "Rent May 2026",
            "INTERNET_BANKING",
            "test-correlation-001"
        );
        t.assignReferenceNumber("TRF-20260518-TESTTEST");
        return t;
    }

    private Transfer buildCompletedTransfer(final UUID transferId, final String ref) {
        Transfer t = Transfer.reconstitute(
            transferId, ref,
            new AccountId(SRC_ID), new AccountId(DEST_ID),
            CUST_ID, CUST_ID,
            Money.of("1500.0000", "THB"),
            "Rent May 2026", "INTERNET_BANKING", "test-correlation-001",
            TransferStatus.PENDING, null, null, 0L
        );
        t.markCompleted();
        return t;
    }

    private Transfer buildFailedTransfer(
            final UUID transferId,
            final String ref,
            final String reason) {
        Transfer t = Transfer.reconstitute(
            transferId, ref,
            new AccountId(SRC_ID), new AccountId(DEST_ID),
            CUST_ID, CUST_ID,
            Money.of("1500.0000", "THB"),
            "Rent May 2026", "INTERNET_BANKING", "test-correlation-001",
            TransferStatus.PENDING, null, null, 0L
        );
        t.markFailed(reason);
        return t;
    }

    private String buildCachedBody(final ObjectMapper mapper, final UUID transferId)
            throws Exception {
        // Minimal JSON that ObjectMapper can deserialize as TransferResult
        return mapper.writeValueAsString(new TransferResult(
            transferId,
            "TRF-20260518-CACHED01",
            TransferStatus.COMPLETED,
            new BigDecimal("1500.0000"),
            "THB",
            SRC_ID,
            DEST_ID,
            "Rent May 2026",
            Instant.now(),
            null,
            IdempotencyStatus.FIRST_WRITE
        ));
    }

    /**
     * Replicates the checksum logic from {@link CreateTransferUseCase} so we can
     * supply a matching checksum in the idempotency record stub.
     */
    private String computeChecksum(final CreateTransferCommand command) throws Exception {
        record Canonical(
            String sourceAccountId,
            String destinationAccountId,
            String amount,
            String currency,
            String memo,
            String channel
        ) {}
        ObjectMapper m = new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        String json = m.writeValueAsString(new Canonical(
            command.sourceAccountId().toString(),
            command.destinationAccountId().toString(),
            command.amount().toPlainString(),
            command.currency(),
            command.memo(),
            command.channel()
        ));
        java.security.MessageDigest digest =
            java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(hash);
    }
}
