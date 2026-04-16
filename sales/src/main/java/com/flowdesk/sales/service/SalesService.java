package com.flowdesk.sales.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.core.context.TenantContext;
import com.flowdesk.core.exception.ConflictException;
import com.flowdesk.core.exception.ResourceNotFoundException;
import com.flowdesk.core.lock.DistributedLockService;
import com.flowdesk.sales.domain.*;
import com.flowdesk.sales.dto.*;
import com.flowdesk.sales.outbox.SalesOutboxEvent;
import com.flowdesk.sales.outbox.SalesOutboxRepository;
import com.flowdesk.sales.repository.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class SalesService {

    private final CustomerRepository customerRepo;
    private final OpportunityRepository opportunityRepo;
    private final SalesOrderRepository orderRepo;
    private final InteractionRepository interactionRepo;
    private final SalesOutboxRepository outboxRepo;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final DistributedLockService lockService;

    public SalesService(CustomerRepository customerRepo,
                        OpportunityRepository opportunityRepo,
                        SalesOrderRepository orderRepo,
                        InteractionRepository interactionRepo,
                        SalesOutboxRepository outboxRepo,
                        ObjectMapper objectMapper,
                        ApplicationEventPublisher eventPublisher,
                        DistributedLockService lockService) {
        this.customerRepo = customerRepo;
        this.opportunityRepo = opportunityRepo;
        this.orderRepo = orderRepo;
        this.interactionRepo = interactionRepo;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.lockService = lockService;
    }

    // ── Customers ─────────────────────────────────────────────────────────────

    @Transactional
    public Customer createCustomer(CreateCustomerRequest req) {
        Customer c = new Customer();
        c.setTenantId(TenantContext.getTenantId());
        c.setCompanyName(req.companyName());
        c.setContactEmail(req.contactEmail());
        c.setCreditLimit(req.creditLimit() != null ? req.creditLimit() : BigDecimal.ZERO);
        c.setPaymentTerms(req.paymentTerms());
        return customerRepo.save(c);
    }

    // ── Opportunities ─────────────────────────────────────────────────────────

    @Transactional
    public Opportunity createOpportunity(CreateOpportunityRequest req) {
        Opportunity opp = new Opportunity();
        opp.setTenantId(TenantContext.getTenantId());
        opp.setCustomerId(req.customerId());
        opp.setStage(req.stage() != null ? req.stage() : "PROSPECT");
        opp.setValue(req.value());
        return opportunityRepo.save(opp);
    }

    @Transactional
    public Opportunity updateOpportunity(UUID id, CreateOpportunityRequest req) {
        Opportunity opp = opportunityRepo.findByIdAndTenantId(id, TenantContext.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Opportunity not found"));

        String previousStage = opp.getStage();
        if (req.stage() != null) opp.setStage(req.stage());
        if (req.value() != null) opp.setValue(req.value());
        Opportunity saved = opportunityRepo.save(opp);

        // On CLOSED_WON: async order creation within 5 seconds
        if ("CLOSED_WON".equals(req.stage()) && !"CLOSED_WON".equals(previousStage)) {
            eventPublisher.publishEvent(new ClosedWonEvent(saved.getId(), saved.getCustomerId(),
                    TenantContext.getTenantId()));
        }
        return saved;
    }

    // ── Orders ────────────────────────────────────────────────────────────────

    @Transactional
    public SalesOrder createOrder(CreateOrderRequest req) {
        UUID tenantId = TenantContext.getTenantId();
        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(req.customerId());
        order.setOpportunityId(req.opportunityId());
        order.setTotalAmount(req.totalAmount() != null ? req.totalAmount() : BigDecimal.ZERO);
        return orderRepo.save(order);
    }

    @Transactional
    public SalesOrder confirmOrder(UUID orderId) {
        String lockKey = "lock:order:" + orderId;
        if (!lockService.tryLock(lockKey, 30)) {
            throw new ConflictException("Order confirmation already in progress");
        }
        try {
        UUID tenantId = TenantContext.getTenantId();
        SalesOrder order = orderRepo.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        // Credit hold check
        Customer customer = customerRepo.findByIdAndTenantId(order.getCustomerId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        BigDecimal outstanding = orderRepo.sumOutstandingByCustomer(order.getCustomerId(), tenantId);
        BigDecimal projected = outstanding.add(order.getTotalAmount());

        if (projected.compareTo(customer.getCreditLimit()) > 0) {
            order.setCreditHold(true);
            SalesOrder saved = orderRepo.save(order);
            publishOutbox("SalesOrder", saved.getId(), "sales.credit-hold", saved);
            return saved;
        }

        order.setStatus("CONFIRMED");
        SalesOrder saved = orderRepo.save(order);
        publishOutbox("SalesOrder", saved.getId(), "sales.order.confirmed", saved);
        return saved;
        } finally {
            lockService.unlock(lockKey);
        }
    }

    @Transactional
    public SalesOrder invoiceOrder(UUID orderId) {
        SalesOrder order = orderRepo.findByIdAndTenantId(orderId, TenantContext.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        order.setStatus("INVOICED");
        return orderRepo.save(order);
    }

    // ── Interactions ──────────────────────────────────────────────────────────

    @Transactional
    public Interaction recordInteraction(CreateInteractionRequest req) {
        Interaction i = new Interaction();
        i.setTenantId(TenantContext.getTenantId());
        i.setCustomerId(req.customerId());
        i.setOpportunityId(req.opportunityId());
        i.setType(req.type());
        i.setNotes(req.notes());
        i.setAuthorId(currentUserId());
        return interactionRepo.save(i);
    }

    // ── Closed-won async order creation ───────────────────────────────────────

    @TransactionalEventListener
    @Async
    public void onClosedWon(ClosedWonEvent event) {
        SalesOrder order = new SalesOrder();
        order.setTenantId(event.tenantId());
        order.setCustomerId(event.customerId());
        order.setOpportunityId(event.opportunityId());
        order.setStatus("DRAFT");
        orderRepo.save(order);
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
            SalesOutboxEvent event = new SalesOutboxEvent();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            outboxRepo.save(event);
        } catch (Exception ignored) {}
    }

    public record ClosedWonEvent(UUID opportunityId, UUID customerId, UUID tenantId) {}
}
