package com.flowdesk.sales.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record CreateCustomerRequest(
        @NotBlank String companyName,
        String contactEmail,
        BigDecimal creditLimit,
        String paymentTerms
) {}
