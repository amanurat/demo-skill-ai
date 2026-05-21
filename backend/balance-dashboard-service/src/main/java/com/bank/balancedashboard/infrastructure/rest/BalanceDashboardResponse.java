package com.bank.balancedashboard.infrastructure.rest;

import com.bank.balancedashboard.domain.model.AccountView;
import com.bank.balancedashboard.domain.model.RankedDashboard;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST response DTO for GET /api/v1/balance-dashboard.
 *
 * <p>Serialization rules (OpenAPI interface contract — locked):
 * <ul>
 *   <li>{@code balance} serialized as JSON STRING (never number) — {@code @JsonSerialize(using=ToStringSerializer)}</li>
 *   <li>{@code balanceAsOf} serialized as ISO 8601 UTC string</li>
 *   <li>{@code meta.cacheHit} is observability only — never displayed to customer</li>
 * </ul>
 */
public class BalanceDashboardResponse {

    private final List<AccountViewDto> accounts;
    private final ResponseMetaDto meta;

    public BalanceDashboardResponse(List<AccountViewDto> accounts, ResponseMetaDto meta) {
        this.accounts = accounts;
        this.meta = meta;
    }

    public List<AccountViewDto> getAccounts() { return accounts; }
    public ResponseMetaDto getMeta() { return meta; }

    /**
     * Factory: builds BalanceDashboardResponse from a domain RankedDashboard.
     */
    public static BalanceDashboardResponse from(RankedDashboard dashboard) {
        List<AccountViewDto> accountDtos = dashboard.accounts().stream()
                .map(AccountViewDto::from)
                .collect(Collectors.toList());

        ResponseMetaDto meta = new ResponseMetaDto(
                dashboard.freshness(),
                dashboard.cacheHit(),
                dashboard.accountCount(),
                dashboard.correlationId()
        );

        return new BalanceDashboardResponse(accountDtos, meta);
    }

    /**
     * Per-account row DTO — balance MUST be serialized as STRING (not number).
     * See OpenAPI field contract: balance type=string, format=^-?\d+\.\d{2}$
     */
    public static class AccountViewDto {
        private final int rank;
        private final UUID accountId;
        private final String accountNumberMasked;
        private final String accountType;

        // CRITICAL: balance MUST be serialized as JSON string, not number (IEEE-754 prevention)
        @JsonSerialize(using = ToStringSerializer.class)
        private final BigDecimal balance;

        private final String currency;
        private final Instant balanceAsOf;
        private final boolean isStale;
        private final String displayLabel;

        private AccountViewDto(int rank, UUID accountId, String accountNumberMasked,
                               String accountType, BigDecimal balance, String currency,
                               Instant balanceAsOf, boolean isStale, String displayLabel) {
            this.rank = rank;
            this.accountId = accountId;
            this.accountNumberMasked = accountNumberMasked;
            this.accountType = accountType;
            this.balance = balance;
            this.currency = currency;
            this.balanceAsOf = balanceAsOf;
            this.isStale = isStale;
            this.displayLabel = displayLabel;
        }

        public static AccountViewDto from(AccountView av) {
            return new AccountViewDto(
                    av.rank(),
                    av.accountId(),
                    av.accountNumberMasked(),
                    av.accountType().name(),
                    av.balance(),
                    av.currency(),
                    av.balanceAsOf(),
                    av.isStale(),
                    av.displayLabel()
            );
        }

        public int getRank() { return rank; }
        public UUID getAccountId() { return accountId; }
        public String getAccountNumberMasked() { return accountNumberMasked; }
        public String getAccountType() { return accountType; }
        public BigDecimal getBalance() { return balance; }
        public String getCurrency() { return currency; }
        public Instant getBalanceAsOf() { return balanceAsOf; }

        @JsonProperty("isStale")
        public boolean isStale() { return isStale; }
        public String getDisplayLabel() { return displayLabel; }
    }

    /**
     * Response metadata DTO.
     * cacheHit is observability only — FE MUST NOT display to customer (Designer §9.3).
     */
    public static class ResponseMetaDto {
        private final String freshness;
        private final boolean cacheHit;
        private final int accountCount;
        private final String correlationId;

        public ResponseMetaDto(String freshness, boolean cacheHit, int accountCount, String correlationId) {
            this.freshness = freshness;
            this.cacheHit = cacheHit;
            this.accountCount = accountCount;
            this.correlationId = correlationId;
        }

        public String getFreshness() { return freshness; }
        public boolean isCacheHit() { return cacheHit; }
        public int getAccountCount() { return accountCount; }
        public String getCorrelationId() { return correlationId; }
    }
}
