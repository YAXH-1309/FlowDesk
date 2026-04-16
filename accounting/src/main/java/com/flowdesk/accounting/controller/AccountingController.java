package com.flowdesk.accounting.controller;

import com.flowdesk.accounting.domain.ApInvoice;
import com.flowdesk.accounting.domain.ArInvoice;
import com.flowdesk.accounting.domain.Budget;
import com.flowdesk.accounting.domain.JournalEntry;
import com.flowdesk.accounting.dto.*;
import com.flowdesk.accounting.service.AccountingService;
import com.flowdesk.core.idempotency.IdempotencyRequired;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounting")
public class AccountingController {

    private final AccountingService accountingService;

    public AccountingController(AccountingService accountingService) {
        this.accountingService = accountingService;
    }

    @PostMapping("/journal-entries")
    @ResponseStatus(HttpStatus.CREATED)
    public JournalEntry postJournalEntry(@Valid @RequestBody PostJournalEntryRequest req) {
        return accountingService.postJournalEntry(req);
    }

    @GetMapping("/accounts/{accountId}/balance")
    public BigDecimal getAccountBalance(@PathVariable UUID accountId) {
        return accountingService.getAccountBalance(accountId);
    }

    @GetMapping("/reports/trial-balance")
    public List<TrialBalanceLine> getTrialBalance() {
        return accountingService.getTrialBalance();
    }

    @GetMapping("/reports/income-statement")
    public List<TrialBalanceLine> getIncomeStatement() {
        return accountingService.getIncomeStatement();
    }

    @GetMapping("/reports/balance-sheet")
    public List<TrialBalanceLine> getBalanceSheet() {
        return accountingService.getBalanceSheet();
    }

    @PostMapping("/ap/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    @IdempotencyRequired
    public ApInvoice createApInvoice(@Valid @RequestBody CreateInvoiceRequest req) {
        return accountingService.createApInvoice(req);
    }

    @PostMapping("/ar/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    @IdempotencyRequired
    public ArInvoice createArInvoice(@Valid @RequestBody CreateInvoiceRequest req) {
        return accountingService.createArInvoice(req);
    }

    @PostMapping("/budgets")
    @ResponseStatus(HttpStatus.CREATED)
    public Budget createBudget(@Valid @RequestBody CreateBudgetRequest req) {
        return accountingService.createBudget(req);
    }
}
