package com.bank.transfer.infrastructure.persistence;

import com.bank.transfer.application.port.out.EventPublisher;
import com.bank.transfer.domain.model.Transfer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Implements {@link EventPublisher} using the Transactional Outbox pattern (ADR-014).
 *
 * <p>Each event is written as a row to {@code transfer_outbox} within the same DB
 * transaction as the business write. A separate {@code @Scheduled} relay poller
 * (not implemented in v1) will read undispatched rows and publish to Kafka.
 *
 * <p>Kafka topic naming convention (v1 stub): {@code transfer.events}.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);
    private static final String AGGREGATE_TYPE = "TRANSFER";
    private static final String TOPIC = "transfer.events";
    private static final short SCHEMA_VERSION = 1;

    private final OutboxJpaRepository outboxJpaRepository;
    private final ObjectMapper objectMapper;

    /** {@inheritDoc} */
    @Override
    public void publishTransferRequested(final Transfer transfer) {
        writeOutboxRow(transfer, "TransferRequested",
            buildPayload(transfer, null));
    }

    /** {@inheritDoc} */
    @Override
    public void publishTransferCompleted(final Transfer transfer) {
        writeOutboxRow(transfer, "TransferCompleted",
            buildPayload(transfer, null));
    }

    /** {@inheritDoc} */
    @Override
    public void publishTransferFailed(final Transfer transfer, final String reason) {
        writeOutboxRow(transfer, "TransferFailed",
            buildPayload(transfer, reason));
    }

    // --- Private helpers ---

    private void writeOutboxRow(
            final Transfer transfer,
            final String eventType,
            final String payloadJson) {
        OutboxJpaEntity entity = new OutboxJpaEntity();
        entity.setOutboxId(UUID.randomUUID());
        entity.setAggregateType(AGGREGATE_TYPE);
        entity.setAggregateId(transfer.getTransferId());
        entity.setEventType(eventType);
        entity.setEventId(UUID.randomUUID());
        entity.setSchemaVersion(SCHEMA_VERSION);
        entity.setTopic(TOPIC);
        entity.setPartitionKey(transfer.getTransferId().toString());
        entity.setHeaders("{}");
        entity.setPayload(payloadJson);
        entity.setCorrelationId(transfer.getCorrelationId());
        entity.setDispatched(false);
        entity.setAttemptCount(0);
        entity.setCreatedAt(Instant.now());
        outboxJpaRepository.save(entity);
        log.info("outbox.written event_type={} transfer_id={} outbox_id={}",
            eventType, transfer.getTransferId(), entity.getOutboxId());
    }

    private String buildPayload(final Transfer transfer, final String failureReason) {
        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("transferId", transfer.getTransferId().toString());
            payload.put("referenceNumber", transfer.getReferenceNumber());
            payload.put("sourceAccountId", transfer.getSourceAccountId().getId().toString());
            payload.put("destinationAccountId",
                transfer.getDestinationAccountId().getId().toString());
            payload.put("amount", transfer.getAmount().toWireString());
            payload.put("currency", transfer.getAmount().getCurrency().getCurrencyCode());
            payload.put("status", transfer.getStatus().name());
            payload.put("channel", transfer.getChannel());
            if (failureReason != null) {
                payload.put("failureReason", failureReason);
            }
            if (transfer.getCompletedAt() != null) {
                payload.put("completedAt", transfer.getCompletedAt().toString());
            }
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}
