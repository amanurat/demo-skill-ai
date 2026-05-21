package com.bank.account.client;

import java.util.UUID;

/**
 * Canonical account DTO from account-service.
 * Source of truth lives in common-libs/account-client-lib (DO NOT REDEFINE in consuming services).
 *
 * <p>Per OpenAPI account-service-extension.openapi.yaml AccountInfo schema.
 * Field {@code balance} is a BigDecimal-as-string (2dp) to avoid IEEE-754 precision loss.
 * For FIXED_DEPOSIT: balance = principal + accrued interest as of balanceAsOf (ASSUMPTION-TL-001).
 */
public class AccountInfo {

    private UUID accountId;
    private String accountNumberMasked;
    private String accountType;  // SAVINGS | CURRENT | FIXED_DEPOSIT | LOAN | CREDIT_CARD
    private String status;        // ACTIVE | DORMANT | CLOSED | FROZEN | INACTIVE
    private String balance;       // BigDecimal-as-string, format ^-?\d+\.\d{2}$
    private String currency;      // ISO 4217, e.g. THB
    private String balanceAsOf;   // ISO 8601 UTC, e.g. 2026-05-21T08:00:00Z

    public AccountInfo() {}

    public AccountInfo(UUID accountId, String accountNumberMasked, String accountType,
                       String status, String balance, String currency, String balanceAsOf) {
        this.accountId = accountId;
        this.accountNumberMasked = accountNumberMasked;
        this.accountType = accountType;
        this.status = status;
        this.balance = balance;
        this.currency = currency;
        this.balanceAsOf = balanceAsOf;
    }

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public String getAccountNumberMasked() { return accountNumberMasked; }
    public void setAccountNumberMasked(String accountNumberMasked) { this.accountNumberMasked = accountNumberMasked; }

    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getBalance() { return balance; }
    public void setBalance(String balance) { this.balance = balance; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getBalanceAsOf() { return balanceAsOf; }
    public void setBalanceAsOf(String balanceAsOf) { this.balanceAsOf = balanceAsOf; }
}
