package com.flowdesk.sales;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.core.context.TenantContext;
import com.flowdesk.core.exception.ResourceNotFoundException;
import com.flowdesk.sales.domain.Customer;
import com.flowdesk.sales.domain.Opportunity;
import com.flowdesk.sales.domain.SalesOrder;
import com.flowdesk.sales.dto.CreateOpportunityRequest;
import com.flowdesk.sales.dto.CreateOrderRequest;
import com.flowdesk.sales.outbox.SalesOutboxEvent;
import com.flowdesk.sales.outbox.SalesOutboxRepository;
import com.flowdesk.sales.repository.CustomerRepository;
import com.flowdesk.sales.repository.OpportunityRepository;
import com.flowdesk.sales.repository.SalesOrderRepository;
import com.flowdesk.sales.repository.InteractionRepository;
import com.flowdesk.sales.service.SalesService;
import net.jqwik.api.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P17 (task 14.5): Sales credit hold is applied for any order exceeding credit limit
 * P18 (task 14.3): Opportunity closed-won triggers order creation
 * Validates: Requirements 8.3, 8.7
 */
class SalesPropertyTest {

    private static final UUID TENANT = UUID.randomUUID();

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void setTenant() {
        TenantContext.setTenantId(TENANT);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(), null, List.of()));
    }

    private SalesService buildService(CustomerRepository customerRepo,
                                       SalesOrderRepository orderRepo,
                                       OpportunityRepository opportunityRepo,
                                       List<SalesOutboxEvent> capturedOutbox,
                                       List<SalesOrder> capturedOrders) {
        InteractionRepository interactionRepo = mock(InteractionRepository.class);
        SalesOutboxRepository outboxRepo = mock(SalesOutboxRepository.class);
        when(outboxRepo.save(any())).thenAnswer(inv -> {
            capturedOutbox.add(inv.getArgument(0));
            return inv.getArgument(0);
        });

        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

        when(orderRepo.save(any())).thenAnswer(inv -> {
            SalesOrder o = inv.getArgument(0);
            setId(o, UUID.randomUUID());
            capturedOrders.add(o);
            return o;
        });

        return new SalesService(customerRepo, opportunityRepo, orderRepo,
                interactionRepo, outboxRepo, new ObjectMapper(), publisher);
    }

    // ── Generators ────────────────────────────────────────────────────────────

    /** Generates a positive credit limit. */
    @Provide
    Arbitrary<BigDecimal> positiveCreditLimit() {
        return Arbitraries.integers().between(100, 10_000).map(BigDecimal::new);
    }

    /** Generates an order amount that exceeds the credit limit. */
    @Provide
    Arbitrary<BigDecimal> exceedingAmount() {
        return Arbitraries.integers().between(10_001, 20_000).map(BigDecimal::new);
    }

    /** Generates an order amount within the credit limit. */
    @Provide
    Arbitrary<BigDecimal> withinAmount() {
        return Arbitraries.integers().between(1, 99).map(BigDecimal::new);
    }

    // ── P17: Credit hold applied when outstanding + order > credit limit ───────

    @Property(tries = 20)
    @Tag("Feature: saas-platform, Property 17: Sales credit hold is applied for any order exceeding credit limit")
    void p17_creditHoldAppliedWhenOrderExceedsCreditLimit(
            @ForAll("positiveCreditLimit") BigDecimal creditLimit,
            @ForAll("exceedingAmount") BigDecimal orderAmount) {

        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Customer customer = new Customer();
        setId(customer, customerId);
        customer.setTenantId(TENANT);
        customer.setCompanyName("Test Corp");
        customer.setCreditLimit(creditLimit);

        SalesOrder order = new SalesOrder();
        setId(order, orderId);
        order.setTenantId(TENANT);
        order.setCustomerId(customerId);
        order.setTotalAmount(orderAmount);
        order.setStatus("DRAFT");

        CustomerRepository customerRepo = mock(CustomerRepository.class);
        when(customerRepo.findByIdAndTenantId(customerId, TENANT)).thenReturn(Optional.of(customer));

        SalesOrderRepository orderRepo = mock(SalesOrderRepository.class);
        when(orderRepo.findByIdAndTenantId(orderId, TENANT)).thenReturn(Optional.of(order));
        // No prior outstanding balance
        when(orderRepo.sumOutstandingByCustomer(customerId, TENANT)).thenReturn(BigDecimal.ZERO);

        OpportunityRepository opportunityRepo = mock(OpportunityRepository.class);

        List<SalesOutboxEvent> capturedOutbox = new ArrayList<>();
        List<SalesOrder> capturedOrders = new ArrayList<>();

        // Override save to capture the saved order
        SalesOutboxRepository outboxRepo = mock(SalesOutboxRepository.class);
        when(outboxRepo.save(any())).thenAnswer(inv -> {
            capturedOutbox.add(inv.getArgument(0));
            return inv.getArgument(0);
        });

        when(orderRepo.save(any())).thenAnswer(inv -> {
            SalesOrder o = inv.getArgument(0);
            capturedOrders.add(o);
            return o;
        });

        SalesService service = new SalesService(customerRepo, opportunityRepo, orderRepo,
                mock(InteractionRepository.class), outboxRepo, new ObjectMapper(),
                mock(ApplicationEventPublisher.class));

        setTenant();
        SalesOrder result = service.confirmOrder(orderId);

        // Order must be on credit hold
        assertThat(result.isCreditHold())
                .as("Order with amount %s exceeding credit limit %s must be on credit hold",
                        orderAmount, creditLimit)
                .isTrue();

        // credit-hold event must be published to outbox
        assertThat(capturedOutbox)
                .as("A sales.credit-hold outbox event must be published")
                .anyMatch(e -> "sales.credit-hold".equals(e.getEventType()));
    }

    @Property(tries = 20)
    @Tag("Feature: saas-platform, Property 17: Sales credit hold is applied for any order exceeding credit limit")
    void p17_noHoldWhenOrderWithinCreditLimit(
            @ForAll("positiveCreditLimit") BigDecimal creditLimit,
            @ForAll("withinAmount") BigDecimal orderAmount) {

        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Customer customer = new Customer();
        setId(customer, customerId);
        customer.setTenantId(TENANT);
        customer.setCompanyName("Test Corp");
        customer.setCreditLimit(creditLimit);

        SalesOrder order = new SalesOrder();
        setId(order, orderId);
        order.setTenantId(TENANT);
        order.setCustomerId(customerId);
        order.setTotalAmount(orderAmount);
        order.setStatus("DRAFT");

        CustomerRepository customerRepo = mock(CustomerRepository.class);
        when(customerRepo.findByIdAndTenantId(customerId, TENANT)).thenReturn(Optional.of(customer));

        SalesOrderRepository orderRepo = mock(SalesOrderRepository.class);
        when(orderRepo.findByIdAndTenantId(orderId, TENANT)).thenReturn(Optional.of(order));
        when(orderRepo.sumOutstandingByCustomer(customerId, TENANT)).thenReturn(BigDecimal.ZERO);

        List<SalesOutboxEvent> capturedOutbox = new ArrayList<>();
        SalesOutboxRepository outboxRepo = mock(SalesOutboxRepository.class);
        when(outboxRepo.save(any())).thenAnswer(inv -> {
            capturedOutbox.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SalesService service = new SalesService(customerRepo, mock(OpportunityRepository.class),
                orderRepo, mock(InteractionRepository.class), outboxRepo, new ObjectMapper(),
                mock(ApplicationEventPublisher.class));

        setTenant();
        SalesOrder result = service.confirmOrder(orderId);

        assertThat(result.isCreditHold())
                .as("Order with amount %s within credit limit %s must NOT be on credit hold",
                        orderAmount, creditLimit)
                .isFalse();

        assertThat(result.getStatus()).isEqualTo("CONFIRMED");

        assertThat(capturedOutbox)
                .as("A sales.order.confirmed outbox event must be published")
                .anyMatch(e -> "sales.order.confirmed".equals(e.getEventType()));
    }

    // ── P18: Closed-won opportunity triggers order creation ───────────────────

    @Property(tries = 20)
    @Tag("Feature: saas-platform, Property 18: Opportunity closed-won triggers order creation")
    void p18_closedWonOpportunityTriggersOrderCreation() throws InterruptedException {
        UUID opportunityId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        Opportunity opp = new Opportunity();
        setId(opp, opportunityId);
        opp.setTenantId(TENANT);
        opp.setCustomerId(customerId);
        opp.setStage("NEGOTIATION");

        OpportunityRepository opportunityRepo = mock(OpportunityRepository.class);
        when(opportunityRepo.findByIdAndTenantId(opportunityId, TENANT)).thenReturn(Optional.of(opp));
        when(opportunityRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<SalesOrder> createdOrders = new ArrayList<>();
        SalesOrderRepository orderRepo = mock(SalesOrderRepository.class);
        when(orderRepo.save(any())).thenAnswer(inv -> {
            SalesOrder o = inv.getArgument(0);
            setId(o, UUID.randomUUID());
            createdOrders.add(o);
            return o;
        });

        // Use a real ApplicationEventPublisher that calls the listener synchronously for testing
        AtomicReference<SalesService> serviceRef = new AtomicReference<>();
        ApplicationEventPublisher publisher = event -> {
            if (event instanceof SalesService.ClosedWonEvent cwe) {
                serviceRef.get().onClosedWon(cwe);
            }
        };

        SalesService service = new SalesService(mock(CustomerRepository.class), opportunityRepo,
                orderRepo, mock(InteractionRepository.class), mock(SalesOutboxRepository.class),
                new ObjectMapper(), publisher);
        serviceRef.set(service);

        setTenant();
        service.updateOpportunity(opportunityId, new CreateOpportunityRequest(customerId, "CLOSED_WON", null));

        // Order must be created (synchronous in test via direct publisher)
        assertThat(createdOrders)
                .as("A sales order must be created when opportunity transitions to CLOSED_WON")
                .isNotEmpty();

        SalesOrder linkedOrder = createdOrders.stream()
                .filter(o -> opportunityId.equals(o.getOpportunityId()))
                .findFirst()
                .orElse(null);

        assertThat(linkedOrder)
                .as("Created order must be linked to the opportunity")
                .isNotNull();

        assertThat(linkedOrder.getCustomerId())
                .as("Created order must reference the same customer")
                .isEqualTo(customerId);

        assertThat(linkedOrder.getTenantId())
                .as("Created order must belong to the same tenant")
                .isEqualTo(TENANT);
    }

    @Property(tries = 20)
    @Tag("Feature: saas-platform, Property 18: Opportunity closed-won triggers order creation")
    void p18_noOrderCreatedForNonClosedWonTransitions(
            @ForAll("nonClosedWonStage") String stage) {

        UUID opportunityId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        Opportunity opp = new Opportunity();
        setId(opp, opportunityId);
        opp.setTenantId(TENANT);
        opp.setCustomerId(customerId);
        opp.setStage("PROSPECT");

        OpportunityRepository opportunityRepo = mock(OpportunityRepository.class);
        when(opportunityRepo.findByIdAndTenantId(opportunityId, TENANT)).thenReturn(Optional.of(opp));
        when(opportunityRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = publishedEvents::add;

        SalesService service = new SalesService(mock(CustomerRepository.class), opportunityRepo,
                mock(SalesOrderRepository.class), mock(InteractionRepository.class),
                mock(SalesOutboxRepository.class), new ObjectMapper(), publisher);

        setTenant();
        service.updateOpportunity(opportunityId, new CreateOpportunityRequest(customerId, stage, null));

        assertThat(publishedEvents)
                .as("No ClosedWonEvent should be published for stage transition to %s", stage)
                .noneMatch(e -> e instanceof SalesService.ClosedWonEvent);
    }

    @Provide
    Arbitrary<String> nonClosedWonStage() {
        return Arbitraries.of("QUALIFIED", "PROPOSAL", "NEGOTIATION", "CLOSED_LOST");
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
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
