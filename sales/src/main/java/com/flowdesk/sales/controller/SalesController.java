package com.flowdesk.sales.controller;

import com.flowdesk.core.idempotency.IdempotencyRequired;
import com.flowdesk.sales.domain.*;
import com.flowdesk.sales.dto.*;
import com.flowdesk.sales.service.SalesService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sales")
public class SalesController {

    private final SalesService salesService;

    public SalesController(SalesService salesService) {
        this.salesService = salesService;
    }

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public Customer createCustomer(@Valid @RequestBody CreateCustomerRequest req) {
        return salesService.createCustomer(req);
    }

    @PostMapping("/opportunities")
    @ResponseStatus(HttpStatus.CREATED)
    public Opportunity createOpportunity(@Valid @RequestBody CreateOpportunityRequest req) {
        return salesService.createOpportunity(req);
    }

    @PutMapping("/opportunities/{id}")
    public Opportunity updateOpportunity(@PathVariable UUID id,
                                          @Valid @RequestBody CreateOpportunityRequest req) {
        return salesService.updateOpportunity(id, req);
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @IdempotencyRequired
    public SalesOrder createOrder(@Valid @RequestBody CreateOrderRequest req) {
        return salesService.createOrder(req);
    }

    @PutMapping("/orders/{id}/confirm")
    public SalesOrder confirmOrder(@PathVariable UUID id) {
        return salesService.confirmOrder(id);
    }

    @PostMapping("/orders/{id}/invoice")
    public SalesOrder invoiceOrder(@PathVariable UUID id) {
        return salesService.invoiceOrder(id);
    }

    @PostMapping("/interactions")
    @ResponseStatus(HttpStatus.CREATED)
    public Interaction recordInteraction(@Valid @RequestBody CreateInteractionRequest req) {
        return salesService.recordInteraction(req);
    }
}
