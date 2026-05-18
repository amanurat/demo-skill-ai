package com.bank.transfer.interfaces.rest.mapper;

import com.bank.transfer.application.port.in.TransferResult;
import com.bank.transfer.interfaces.rest.dto.TransferResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper converting {@link TransferResult} (application layer) to
 * {@link TransferResponse} (HTTP DTO).
 *
 * <p>No JPA entities pass through this mapper — domain isolation is maintained.
 * The component model is {@code spring} (configured globally via compiler arg
 * {@code -Amapstruct.defaultComponentModel=spring}).
 */
@Mapper
public interface TransferMapper {

    /**
     * Maps a {@link TransferResult} to the HTTP response DTO.
     *
     * <p>Conversion notes:
     * <ul>
     *   <li>{@code amount} (BigDecimal) → plain decimal string via {@link #amountToString}</li>
     *   <li>{@code status} (enum) → its {@code name()} string</li>
     *   <li>{@code idempotencyStatus} (enum) → its {@code name()} string</li>
     * </ul>
     *
     * @param result the application-layer result
     * @return the HTTP response DTO
     */
    @Mapping(target = "amount", expression = "java(result.amount().toPlainString())")
    @Mapping(target = "status", expression = "java(result.status().name())")
    @Mapping(target = "idempotencyStatus",
        expression = "java(result.idempotencyStatus().name())")
    TransferResponse toResponse(TransferResult result);
}
