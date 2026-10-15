package com.retailerp.accounting;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AccountingDtos {

    private AccountingDtos() {
    }

    public record AccountResponse(String code, String name, AccountType type, boolean system) {
    }

    public record AccountRequest(@NotBlank @Pattern(regexp = "^[0-9]{2,10}$") String code, @NotBlank @Size(max = 150) String name,
            @NotNull AccountType type) {
    }

    public record LineResponse(int lineNo, String accountCode, String accountName, BigDecimal debit, BigDecimal credit, String memo) {
    }

    public record EntryResponse(UUID id, String number, LocalDate entryDate, String description, String sourceType, UUID sourceId,
            BigDecimal total, List<LineResponse> lines, Instant createdAt) {
    }

    public record ManualLine(@NotBlank String accountCode,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal debit,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal credit,
            @Size(max = 200) String memo) {
    }

    public record ManualEntryRequest(LocalDate entryDate, @NotBlank @Size(max = 300) String description,
            @Size(min = 2, max = 100) List<@Valid ManualLine> lines) {
    }

    /** One row of the trial balance or of a financial statement. balance is signed in the account's normal direction. */
    public record AccountBalance(String code, String name, AccountType type, BigDecimal debit, BigDecimal credit, BigDecimal balance) {
    }

    public record TrialBalance(LocalDate from, LocalDate to, List<AccountBalance> accounts, BigDecimal totalDebit, BigDecimal totalCredit) {
    }

    public record LedgerRow(LocalDate date, UUID entryId, String entryNumber, String description, BigDecimal debit, BigDecimal credit,
            BigDecimal balance) {
    }

    public record Ledger(String code, String name, AccountType type, LocalDate from, LocalDate to, BigDecimal opening,
            List<LedgerRow> rows, BigDecimal closing) {
    }

    public record ProfitAndLoss(LocalDate from, LocalDate to, List<AccountBalance> income, BigDecimal totalIncome,
            List<AccountBalance> expenses, BigDecimal totalExpenses, BigDecimal netResult) {
    }

    public record BalanceSheet(LocalDate date, List<AccountBalance> assets, BigDecimal totalAssets, List<AccountBalance> liabilities,
            BigDecimal totalLiabilities, List<AccountBalance> equity, BigDecimal currentResult, BigDecimal totalEquity,
            BigDecimal totalLiabilitiesAndEquity) {
    }

    public record VatRow(BigDecimal rate, BigDecimal base, BigDecimal tax) {
    }

    public record VatReport(LocalDate from, LocalDate to, List<VatRow> sales, BigDecimal collected, List<VatRow> purchases,
            BigDecimal deductible, BigDecimal payable) {
    }
}
