package com.retailerp.accounting;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.retailerp.accounting.AccountingDtos.AccountRequest;
import com.retailerp.accounting.AccountingDtos.AccountResponse;
import com.retailerp.accounting.AccountingDtos.BalanceSheet;
import com.retailerp.accounting.AccountingDtos.EntryResponse;
import com.retailerp.accounting.AccountingDtos.Ledger;
import com.retailerp.accounting.AccountingDtos.ManualEntryRequest;
import com.retailerp.accounting.AccountingDtos.ProfitAndLoss;
import com.retailerp.accounting.AccountingDtos.TrialBalance;
import com.retailerp.accounting.AccountingDtos.VatReport;
import com.retailerp.common.PageResponse;
import com.retailerp.security.Access;
import com.retailerp.subscription.Module;
import com.retailerp.subscription.RequiresModule;

import jakarta.validation.Valid;

/** Reading the books: managers and admins. Changing them (manual entries, new accounts): admins only. */
@RestController
@RequestMapping("/api/accounting")
@RequiresModule(Module.ACCOUNTING)
@PreAuthorize(Access.MANAGE)
public class AccountingController {

    private final AccountingService accounting;

    public AccountingController(AccountingService accounting) {
        this.accounting = accounting;
    }

    @GetMapping("/accounts")
    public List<AccountResponse> accounts() {
        return accounting.accounts();
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Access.ADMIN)
    public AccountResponse addAccount(@Valid @RequestBody AccountRequest request) {
        return accounting.addAccount(request);
    }

    @GetMapping("/journal")
    public PageResponse<EntryResponse> journal(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(defaultValue = "") String source,
            @PageableDefault(size = 50, sort = {"entryDate", "createdAt"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return accounting.journal(from, to, source, pageable);
    }

    @GetMapping("/journal/{id}")
    public EntryResponse entry(@PathVariable UUID id) {
        return accounting.entry(id);
    }

    /** All entries posted for one document (a sale, a bill...). */
    @GetMapping("/journal/by-source/{sourceId}")
    public List<EntryResponse> forSource(@PathVariable UUID sourceId) {
        return accounting.forSource(sourceId);
    }

    @PostMapping("/journal")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Access.ADMIN)
    public EntryResponse manualEntry(@Valid @RequestBody ManualEntryRequest request) {
        return accounting.manualEntry(request);
    }

    @GetMapping("/trial-balance")
    public TrialBalance trialBalance(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return accounting.trialBalance(from, to);
    }

    @GetMapping("/ledger/{code}")
    public Ledger ledger(@PathVariable String code, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return accounting.ledger(code, from, to);
    }

    @GetMapping("/profit-and-loss")
    public ProfitAndLoss profitAndLoss(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return accounting.profitAndLoss(from, to);
    }

    @GetMapping("/balance-sheet")
    public BalanceSheet balanceSheet(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return accounting.balanceSheet(date);
    }

    @GetMapping("/vat")
    public VatReport vat(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return accounting.vat(from, to);
    }
}
