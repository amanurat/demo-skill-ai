package com.bank.transfer.application.usecase;

import com.bank.transfer.application.port.in.CreateTransferCommand;
import com.bank.transfer.application.port.in.CreateTransferPort;
import com.bank.transfer.application.port.in.TransferResult;
import com.bank.transfer.application.port.in.TransferResult.IdempotencyStatus;
import com.bank.transfer.application.port.out.AccountClient;
import com.bank.transfer.application.port.out.AccountInfo;
import com.bank.transfer.application.port.out.EventPublisher;
import com.bank.transfer.application.port.out.IdempotencyRecord;
import com.bank.transfer.application.port.out.IdempotencyRepository;
import com.bank.transfer.application.port.out.TransferRepository;
import com.bank.transfer.application.saga.TransferSaga;
import com.bank.transfer.domain.exception.IdempotencyKeyConflictException;
import com.bank.transfer.domain.exception.TransferNotFoundException;
import com.bank.transfer.domain.model.AccountId;
import com.bank.transfer.domain.model.Money;
import com.bank.transfer.domain.model.Transfer;
import com.bank.transfer.domain.model.TransferStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service implementing US-001 (happy path) and US-003 (idempotency).
 *
 * <p>Transaction boundary: {@code @Transactional} is on {@link #execute} and
 * {@link #findById} — never on the controller. Isolation is READ_COMMITTED
 * (PostgreSQL default); optimistic locking handles concurrency on the transfer row.
 *
 * <p>Idempotency protocol (ADR-013):
 * <ol>
 *   <li>Hash the Idempotency-Key with SHA-256 → {@code keyHash}</li>
 *   <li>Hash the canonical request body → {@code requestChecksum}</li>
 *   <li>SELECT from transfer_idempotency WHERE (keyHash, customerId) within TTL</li>
 *   <li>Hit + equal checksum → return cached response</li>
 *   <li>Hit + different checksum → 409 IDEMPOTENCY_KEY_CONFLICT</li>
 *   <li>Miss → INSERT PENDING placeholder, run saga, UPDATE to final state</li>
 * </ol>
 */
@Service
public class CreateTransferUseCase implements CreateTransferPort {

    private static final Logger log = LoggerFactory.getLogger(CreateTransferUseCase.class);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String REFERENCE_PREFIX = "TRF-";

    private final TransferRepository transferRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final AccountClient accountClient;
    private final EventPublisher eventPublisher;
    private final TransferSaga transferSaga;
    private final ObjectMapper objectMapper;

    /**
     * Constructor injection.
     *
     * @param transferRepository    persistence port for Transfer aggregates
     * @param idempotencyRepository persistence port for idempotency records
     * @param accountClient         outbound port for account-service (stub in v1)
     * @param eventPublisher        outbound port for outbox event writing
     * @param transferSaga          saga coordinator
     * @param objectMapper          JSON serializer for checksum + cache body
     */
    public CreateTransferUseCase(
            final TransferRepository transferRepository,
            final IdempotencyRepository idempotencyRepository,
            final AccountClient accountClient,
            final EventPublisher eventPublisher,
            final TransferSaga transferSaga,
            final ObjectMapper objectMapper) {
        this.transferRepository = transferRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.accountClient = accountClient;
        this.eventPublisher = eventPublisher;
        this.transferSaga = transferSaga;
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The {@code @Transactional} boundary wraps idempotency check, business logic,
     * and outbox write in a single DB transaction. Isolation=READ_COMMITTED per ADR
     * implementation note; optimistic lock on Transfer handles concurrent retries.
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @PreAuthorize("isAuthenticated()")
    public TransferResult execute(final CreateTransferCommand command) {
        final String keyHash = sha256(command.idempotencyKey());
        final String requestChecksum = sha256(canonicalize(command));

        log.info("transfer.execute start src={} correlation_id={}",
            maskAccountId(command.sourceAccountId()), command.correlationId());

        // --- Step 1: Idempotency check (FIRST DB action in transaction) ---
        Optional<IdempotencyRecord> existing =
            idempotencyRepository.findValid(keyHash, command.customerId());

        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!record.requestChecksum().equals(requestChecksum)) {
                log.warn("idempotency.conflict key_hash_prefix={} correlation_id={}",
                    keyHash.substring(0, 8), command.correlationId());
                throw new IdempotencyKeyConflictException();
            }
            // Same key + same payload → return cached response
            log.info("idempotency.replay transfer_id={} correlation_id={}",
                record.transferId(), command.correlationId());
            return deserializeCachedResult(record, IdempotencyStatus.IDEMPOTENT_REPLAY);
        }

        // --- Step 2: JWT ownership check (stub: assume sub == customerId) ---
        // In v1 the controller extracts customerId from JWT sub and passes it as command.customerId().
        // A real check calls account-service to verify sourceAccountId belongs to customerId.
        AccountInfo sourceAccount = accountClient.getAccountInfo(command.sourceAccountId());
        log.info("transfer.ownership_check account_status={}", sourceAccount.status());

        // --- Step 3: Build domain aggregate ---
        Transfer transfer = Transfer.create(
            new AccountId(command.sourceAccountId()),
            new AccountId(command.destinationAccountId()),
            command.customerId(),      // initiatorUserId (stub: same as customerId in v1)
            command.customerId(),
            Money.of(command.amount(), command.currency()),
            command.memo(),
            command.channel() != null ? command.channel() : "INTERNET_BANKING",
            command.correlationId()
        );

        // --- Step 4: Assign reference number ---
        transfer.assignReferenceNumber(generateReferenceNumber(transfer.getTransferId()));

        // --- Step 5: Write PENDING idempotency placeholder (same tx) ---
        idempotencyRepository.insertPending(
            keyHash, command.customerId(), requestChecksum,
            command.correlationId(), IDEMPOTENCY_TTL);

        // --- Step 6: Persist PENDING transfer (same tx) ---
        Transfer saved = transferRepository.save(transfer);

        // --- Step 7: Publish TransferRequested to outbox (same tx) ---
        eventPublisher.publishTransferRequested(saved);

        // --- Step 8: Execute saga (stub in v1) ---
        Transfer processed = transferSaga.execute(saved);

        // --- Step 9: Persist final status ---
        Transfer finalTransfer = transferRepository.save(processed);

        // --- Step 10: Update idempotency record to final state ---
        TransferResult result = toResult(finalTransfer, IdempotencyStatus.FIRST_WRITE);
        String cachedBody = serializeResult(result);

        String finalStatus = finalTransfer.getStatus() == TransferStatus.COMPLETED
            ? "COMPLETED" : "FAILED";
        int httpCode = finalTransfer.getStatus() == TransferStatus.COMPLETED ? 200 : 422;

        idempotencyRepository.updateResult(
            keyHash, command.customerId(), finalTransfer.getTransferId(),
            finalStatus, httpCode, cachedBody);

        log.info("transfer.execute done transfer_id={} status={} correlation_id={}",
            finalTransfer.getTransferId(), finalTransfer.getStatus(), command.correlationId());

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public TransferResult findById(final UUID transferId, final UUID customerId) {
        Transfer transfer = transferRepository.findById(transferId)
            .orElseThrow(() -> new TransferNotFoundException(transferId));

        // Ownership: in v1 we check initiatorCustomerId matches JWT subject
        if (!transfer.getInitiatorCustomerId().equals(customerId)) {
            throw new TransferNotFoundException(transferId); // 404 not 403 — don't leak existence
        }

        return toResult(transfer, IdempotencyStatus.FIRST_WRITE);
    }

    // --- Private helpers ---

    private String sha256(final String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Produces a canonical (deterministic) JSON string of the command fields
     * that constitute the logical request payload for checksum comparison.
     * Excludes correlation-id and idempotency-key (they are envelope, not payload).
     */
    private String canonicalize(final CreateTransferCommand command) {
        try {
            // Use a sorted-key record to guarantee field ordering in JSON output
            record Canonical(
                String sourceAccountId,
                String destinationAccountId,
                String amount,
                String currency,
                String memo,
                String channel
            ) {}
            return objectMapper.writeValueAsString(new Canonical(
                command.sourceAccountId().toString(),
                command.destinationAccountId().toString(),
                command.amount().toPlainString(),
                command.currency(),
                command.memo(),
                command.channel()
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to canonicalize command for checksum", e);
        }
    }

    /**
     * Generates a server-side reference number in the format TRF-YYYYMMDD-XXXXXXXX.
     * The suffix is the first 8 hex chars of the transfer UUID (uppercase).
     * Uniqueness is enforced by the DB UNIQUE constraint on reference_number.
     */
    private String generateReferenceNumber(final UUID transferId) {
        String today = java.time.LocalDate.now(
            java.time.ZoneId.of("Asia/Bangkok")).toString().replace("-", "");
        String suffix = transferId.toString().replace("-", "").substring(0, 8).toUpperCase();
        return REFERENCE_PREFIX + today + "-" + suffix;
    }

    private TransferResult toResult(final Transfer transfer, final IdempotencyStatus idempotencyStatus) {
        return new TransferResult(
            transfer.getTransferId(),
            transfer.getReferenceNumber(),
            transfer.getStatus(),
            transfer.getAmount().getAmount(),
            transfer.getAmount().getCurrency().getCurrencyCode(),
            transfer.getSourceAccountId().getId(),
            transfer.getDestinationAccountId().getId(),
            transfer.getMemo(),
            transfer.getCompletedAt(),
            transfer.getFailureReason(),
            idempotencyStatus
        );
    }

    private String serializeResult(final TransferResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize TransferResult for idempotency cache", e);
        }
    }

    private TransferResult deserializeCachedResult(
            final IdempotencyRecord record,
            final IdempotencyStatus idempotencyStatus) {
        try {
            TransferResult cached = objectMapper.readValue(
                record.cachedResponseBody(), TransferResult.class);
            // Reconstruct with correct idempotency status (replay)
            return new TransferResult(
                cached.transferId(),
                cached.referenceNumber(),
                cached.status(),
                cached.amount(),
                cached.currency(),
                cached.sourceAccountId(),
                cached.destinationAccountId(),
                cached.memo(),
                cached.completedAt(),
                cached.failureReason(),
                idempotencyStatus
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize cached idempotency response", e);
        }
    }

    private String maskAccountId(final UUID accountId) {
        String s = accountId.toString();
        return "****-" + s.substring(s.length() - 4);
    }
}
