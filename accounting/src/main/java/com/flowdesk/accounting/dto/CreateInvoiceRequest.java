package com.flowdesk.accounting.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateInvoiceRequest(
        @NotNull UUID partyId,   // supplierId for AP, customerId for AR
        @NotNull BigDecimal amount,
        LocalDate dueDate
) {}
