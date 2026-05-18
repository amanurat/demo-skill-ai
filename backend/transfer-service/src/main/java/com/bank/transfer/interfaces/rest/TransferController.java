package com.bank.transfer.interfaces.rest;

import com.bank.transfer.application.port.in.CreateTransferCommand;
import com.bank.transfer.application.port.in.CreateTransferPort;
import com.bank.transfer.application.port.in.TransferResult;
import com.bank.transfer.interfaces.rest.dto.TransferRequest;
import com.bank.transfer.interfaces.rest.dto.TransferResponse;
import com.bank.transfer.interfaces.rest.mapper.TransferMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * REST controller for the Transfer Service API (v1).
 *
 * <p>Implements:
 * <ul>
 *   <li>POST /api/v1/transfers — US-001 happy path + US-003 idempotency</li>
 *   <li>GET  /api/v1/transfers/{transferId} — transfer status polling</li>
 * </ul>
 *
 * <p>Design rules:
 * <ul>
 *   <li>No {@code @Transactional} here — transaction boundary is at the use-case layer.</li>
 *   <li>No domain objects in request/response — DTOs only; MapStruct handles conversion.</li>
 *   <li>Idempotency-Key header is required; missing header → 400 via exception handler.</li>
 *   <li>Customer UUID is extracted from JWT subject in v1 stub (fixed UUID until real JWT).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
@Tag(name = "Transfers", description = "Intra-bank money transfer operations")
public class TransferController {

    private static final Logger log = LoggerFactory.getLogger(TransferController.class);

    /**
     * Stub customer UUID used when no real JWT is present in v1.
     * In production this is extracted from the validated JWT {@code sub} claim.
     */
    private static final UUID STUB_CUSTOMER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final CreateTransferPort createTransferPort;
    private final TransferMapper transferMapper;

    /**
     * POST /api/v1/transfers — initiate an intra-bank THB transfer.
     *
     * <p>The {@code Idempotency-Key} header is mandatory (UUID v4). Duplicate requests
     * with the same key and identical payload return the cached response with
     * {@code idempotencyStatus=IDEMPOTENT_REPLAY}. A different payload on the same key
     * returns 409.
     *
     * @param idempotencyKey client-generated UUID v4 for safe retries
     * @param requestId      optional client correlation id echoed back in response header
     * @param request        validated transfer request body
     * @return 200 with TransferResponse (COMPLETED or FAILED)
     */
    @PostMapping
    @Operation(
        summary = "Create an intra-bank transfer",
        operationId = "createTransfer"
    )
    public ResponseEntity<TransferResponse> createTransfer(
            @Parameter(description = "Client UUID v4 for idempotent retries", required = true)
            @RequestHeader("Idempotency-Key") final String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) final String requestId,
            @Valid @RequestBody final TransferRequest request) {

        String correlationId = resolveCorrelationId(requestId);
        MDC.put("correlationId", correlationId);
        MDC.put("idempotencyKeyPrefix", idempotencyKey.length() >= 8
            ? idempotencyKey.substring(0, 8) : idempotencyKey);

        log.info("transfer.create.request channel={} correlation_id={}",
            request.channel(), correlationId);

        CreateTransferCommand command = new CreateTransferCommand(
            request.sourceAccountId(),
            request.destinationAccountId(),
            new BigDecimal(request.amount()),
            request.currency(),
            request.memo(),
            request.channel() != null ? request.channel() : "INTERNET_BANKING",
            idempotencyKey,
            STUB_CUSTOMER_ID,
            correlationId
        );

        TransferResult result = createTransferPort.execute(command);
        TransferResponse response = transferMapper.toResponse(result);

        log.info("transfer.create.done transfer_id={} status={} idempotency_status={}",
            result.transferId(), result.status(), result.idempotencyStatus());

        return ResponseEntity.ok()
            .header("X-Request-Id", correlationId)
            .body(response);
    }

    /**
     * GET /api/v1/transfers/{transferId} — fetch current status of a transfer.
     *
     * <p>The calling customer must be the initiator of the transfer; otherwise 404
     * is returned (obscure-existence policy — never return 403 for financial resources).
     *
     * @param transferId server-assigned transfer UUID from the POST response
     * @param requestId  optional client correlation id
     * @return 200 with current TransferResponse, or 404 if not found / not owned
     */
    @GetMapping("/{transferId}")
    @Operation(
        summary = "Get transfer status",
        operationId = "getTransfer"
    )
    public ResponseEntity<TransferResponse> getTransfer(
            @PathVariable final UUID transferId,
            @RequestHeader(value = "X-Request-Id", required = false) final String requestId) {

        String correlationId = resolveCorrelationId(requestId);
        MDC.put("correlationId", correlationId);

        log.info("transfer.get.request transfer_id={} correlation_id={}",
            transferId, correlationId);

        TransferResult result = createTransferPort.findById(transferId, STUB_CUSTOMER_ID);
        TransferResponse response = transferMapper.toResponse(result);

        return ResponseEntity.ok()
            .header("X-Request-Id", correlationId)
            .body(response);
    }

    // --- Private helpers ---

    private String resolveCorrelationId(final String requestId) {
        return (requestId != null && !requestId.isBlank())
            ? requestId
            : UUID.randomUUID().toString();
    }
}
