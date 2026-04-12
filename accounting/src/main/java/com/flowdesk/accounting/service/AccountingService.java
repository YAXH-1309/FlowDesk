package com.flowdesk.accounting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.accounting.domain.*;
import com.flowdesk.accounting.dto.*;
import com.flowdesk.accounting.outbox.AccountingOutboxEvent;
import com.flowdesk.accounting.outbox.AccountingOutboxRepository;
import com.flowdesk.accounting.repository.*;
import com.flowdesk.core.context.TenantContext;
import com.flowdesk.core.exception.BusinessRuleException;
import com.flowdesk.core.exception.ResourceNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AccountingService {

    private final AccountRepository accountRepo;
    private final JournalEntryRepository journalRepo;
    private final ApInvoiceRepository apRepo;
    private final ArInvoiceRepository arRepo;
    private final BudgetRepository budgetRepo;
    private final AccountingOutboxRepository outboxRepo;
    private final ObjectMapper objectMapper;

    public AccountingService(AccountRepository accountRepo,
                              JournalEntryRepository journalRepo,
                              ApInvoiceRepository apRepo,
                              ArInvoiceRepository arRepo,
                              BudgetRepository budgetRepo,
                              AccountingOutboxRepository outboxRepo,
                              ObjectMapper objectMapper) {
        this.accountRepo = accountRepo;
        this.journalRepo = journalRepo;
        this.apRepo = apRepo;
        this.arRepo = arRepo;
        this.budgetRepo = budgetRepo;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
    }

    // ── Journal Entries ───────────────────────────────────────────────────────

    @Transactional
    public JournalEntry postJournalEntry(PostJournalEntryRequest req) {
        // Validate double-entry invariant: SUM(amounts) == 0
        BigDecimal sum = req.lines().stream()
                .map(PostJournalEntryRequest.LineRequest::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (sum.compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessRuleException(
                    "Journal entry imbalanced by " + sum.toPlainString());
        }

        UUID tenantId = TenantContext.getTenantId();
        JournalEntry entry = new JournalEntry();
        entry.setTenantId(tenantId);
        entry.setDescription(req.description());
        entry.setPostedBy(currentUserId());

        List<JournalLine> lines = req.lines().stream().map(l -> {
            JournalLine line = new JournalLine();
            line.setAccountId(l.accountId());
            line.setAmount(l.amount());
            line.setDescription(l.description());
            return line;
        }).toList();
        entry.setLines(lines);

        // Update account balances atomically using SELECT FOR UPDATE
        for (PostJournalEntryRequest.LineRequest l : req.lines()) {
            if (l.accountId() != null) {
                accountRepo.findByIdAndTenantIdForUpdate(l.accountId(), tenantId)
                        .ifPresent(account -> {
                            account.setBalance(account.getBalance().add(l.amount()));
                            accountRepo.save(account);
                        });
            }
        }

        return journalRepo.save(entry);
    }

    // ── Account Queries ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BigDecimal getAccountBalance(UUID accountId) {
        return accountRepo.findByIdAndTenantId(accountId, TenantContext.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"))
                .getBalance();
    }

    @Transactional(readOnly = true)
    public List<TrialBalanceLine> getTrialBalance() {
        return accountRepo.findByTenantId(TenantContext.getTenantId()).stream()
                .map(a -> new TrialBalanceLine(a.getId(), a.getCode(), a.getName(),
                        a.getType(), a.getBalance()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TrialBalanceLine> getIncomeStatement() {
        return accountRepo.findByTenantId(TenantContext.getTenantId()).stream()
                .filter(a -> a.getType().equals("REVENUE") || a.getType().equals("EXPENSE"))
                .map(a -> new TrialBalanceLine(a.getId(), a.getCode(), a.getName(),
                        a.getType(), a.getBalance()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TrialBalanceLine> getBalanceSheet() {
        return accountRepo.findByTenantId(TenantContext.getTenantId()).stream()
                .filter(a -> a.getType().equals("ASSET") || a.getType().equals("LIABILITY")
                        || a.getType().equals("EQUITY"))
                .map(a -> new TrialBalanceLine(a.getId(), a.getCode(), a.getName(),
                        a.getType(), a.getBalance()))
                .toList();
    }

    // ── AP Invoices ───────────────────────────────────────────────────────────

    @Transactional
    public ApInvoice createApInvoice(CreateInvoiceRequest req) {
        ApInvoice invoice = new ApInvoice();
        invoice.setTenantId(TenantContext.getTenantId());
        invoice.setSupplierId(req.partyId());
        invoice.setAmount(req.amount());
        invoice.setDueDate(req.dueDate());
        return apRepo.save(invoice);
    }

    // ── AR Invoices ───────────────────────────────────────────────────────────

    @Transactional
    public ArInvoice createArInvoice(CreateInvoiceRequest req) {
        ArInvoice invoice = new ArInvoice();
        invoice.setTenantId(TenantContext.getTenantId());
        invoice.setCustomerId(req.partyId());
        invoice.setAmount(req.amount());
        invoice.setDueDate(req.dueDate());
        return arRepo.save(invoice);
    }

    // ── Budget ────────────────────────────────────────────────────────────────

    @Transactional
    public Budget createBudget(CreateBudgetRequest req) {
        Budget budget = new Budget();
        budget.setTenantId(TenantContext.getTenantId());
        budget.setCostCenter(req.costCenter());
        budget.setFiscalPeriod(req.fiscalPeriod());
        if (req.allocated() != null) budget.setAllocated(req.allocated());
        if (req.committed() != null) budget.setCommitted(req.committed());
        if (req.actualSpend() != null) budget.setActualSpend(req.actualSpend());
        return budgetRepo.save(budget);
    }

    // ── Overdue invoice scheduler (called by @Scheduled) ─────────────────────

    @Transactional
    public void markOverdueArInvoices() {
        arRepo.findOverdue(java.time.LocalDate.now()).forEach(invoice -> {
            invoice.setStatus("OVERDUE");
            arRepo.save(invoice);
            publishOutbox("ArInvoice", invoice.getId(), "accounting.invoice.overdue", invoice);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String s) {
            try { return UUID.fromString(s); } catch (IllegalArgumentException ignored) {}
        }
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }

    private void publishOutbox(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        try {
            AccountingOutboxEvent event = new AccountingOutboxEvent();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            outboxRepo.save(event);
        } catch (Exception ignored) {}
    }
}
