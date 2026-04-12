package com.flowdesk.accounting.scheduler;

import com.flowdesk.accounting.service.AccountingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Checks for overdue AR invoices every hour and publishes events. */
@Component
public class OverdueInvoiceScheduler {

    private final AccountingService accountingService;

    public OverdueInvoiceScheduler(AccountingService accountingService) {
        this.accountingService = accountingService;
    }

    @Scheduled(fixedDelay = 3_600_000) // every hour
    public void checkOverdue() {
        accountingService.markOverdueArInvoices();
    }
}
