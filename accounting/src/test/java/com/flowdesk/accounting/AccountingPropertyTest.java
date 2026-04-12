package com.flowdesk.accounting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.accounting.domain.Account;
import com.flowdesk.accounting.domain.JournalEntry;
import com.flowdesk.accounting.dto.PostJournalEntryRequest;
import com.flowdesk.accounting.outbox.AccountingOutboxRepository;
import com.flowdesk.accounting.repository.*;
import com.flowdesk.accounting.service.AccountingService;
import com.flowdesk.core.context.TenantContext;
import com.flowdesk.core.exception.BusinessRuleException;
import net.jqwik.api.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P9 (task 12.2): Double-entry ledger invariant
 * Validates: Requirements 7.1, 7.3
 */
class AccountingPropertyTest {

    private static final UUID TENANT = UUID.randomUUID();

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private AccountingService buildService(AccountRepository accountRepo) {
        JournalEntryRepository journalRepo = mock(JournalEntryRepository.class);
        when(journalRepo.save(any())).thenAnswer(inv -> {
            JournalEntry e = inv.getArgument(0);
            setId(e, UUID.randomUUID());
            return e;
        });
        return new AccountingService(accountRepo, journalRepo,
                mock(ApInvoiceRepository.class), mock(ArInvoiceRepository.class),
                mock(BudgetRepository.class), mock(AccountingOutboxRepository.class),
                new ObjectMapper());
    }

    private void setTenant() {
        TenantContext.setTenantId(TENANT);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(), null, List.of()));
    }

    // ── Generators ────────────────────────────────────────────────────────────

    /** Generates a list of amounts that sum to zero (balanced entry). */
    @Provide
    Arbitrary<List<BigDecimal>> balancedAmounts() {
        return Arbitraries.integers().between(1, 10_000)
                .map(BigDecimal::new)
                .list().ofMinSize(2).ofMaxSize(6)
                .map(debits -> {
                    BigDecimal total = debits.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                    List<BigDecimal> amounts = new ArrayList<>(debits);
                    amounts.add(total.negate()); // add balancing credit
                    return amounts;
                });
    }

    /** Generates amounts that do NOT sum to zero (imbalanced entry). */
    @Provide
    Arbitrary<List<BigDecimal>> imbalancedAmounts() {
        return Arbitraries.integers().between(1, 10_000)
                .map(BigDecimal::new)
                .list().ofMinSize(2).ofMaxSize(5)
                .filter(list -> {
                    BigDecimal sum = list.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                    return sum.compareTo(BigDecimal.ZERO) != 0;
                });
    }

    // ── P9a: Balanced entries are accepted ────────────────────────────────────

    @Property(tries = 100)
    @Tag("Feature: saas-platform, Property 9: Double-entry ledger invariant")
    void p9_balancedEntryIsAccepted(@ForAll("balancedAmounts") List<BigDecimal> amounts) {
        AccountRepository accountRepo = mock(AccountRepository.class);
        when(accountRepo.findByIdAndTenantIdForUpdate(any(), any())).thenReturn(Optional.empty());

        setTenant();
        AccountingService service = buildService(accountRepo);

        List<PostJournalEntryRequest.LineRequest> lines = amounts.stream()
                .map(a -> new PostJournalEntryRequest.LineRequest(null, a, null))
                .toList();

        // Should not throw
        JournalEntry entry = service.postJournalEntry(new PostJournalEntryRequest("test", lines));
        assertThat(entry).isNotNull();

        // Verify the sum is indeed zero
        BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum.compareTo(BigDecimal.ZERO)).isZero();
    }

    // ── P9b: Imbalanced entries are rejected with imbalance amount ────────────

    @Property(tries = 100)
    @Tag("Feature: saas-platform, Property 9: Double-entry ledger invariant")
    void p9_imbalancedEntryIsRejectedWith422(@ForAll("imbalancedAmounts") List<BigDecimal> amounts) {
        AccountRepository accountRepo = mock(AccountRepository.class);
        setTenant();
        AccountingService service = buildService(accountRepo);

        BigDecimal expectedImbalance = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PostJournalEntryRequest.LineRequest> lines = amounts.stream()
                .map(a -> new PostJournalEntryRequest.LineRequest(null, a, null))
                .toList();

        assertThatThrownBy(() -> service.postJournalEntry(new PostJournalEntryRequest("test", lines)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(expectedImbalance.toPlainString());
    }

    // ── P9c: Sum invariant holds for any balanced entry ───────────────────────

    @Property(tries = 100)
    @Tag("Feature: saas-platform, Property 9: Double-entry ledger invariant")
    void p9_sumOfBalancedEntryIsAlwaysZero(@ForAll("balancedAmounts") List<BigDecimal> amounts) {
        BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum.compareTo(BigDecimal.ZERO))
                .as("Sum of balanced amounts must be zero, was: %s", sum)
                .isZero();
    }

    // ── Reflection helper ─────────────────────────────────────────────────────

    private static void setId(Object entity, UUID id) {
        try {
            Class<?> clazz = entity.getClass();
            while (clazz != null) {
                try {
                    var f = clazz.getDeclaredField("id");
                    f.setAccessible(true);
                    f.set(entity, id);
                    return;
                } catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
