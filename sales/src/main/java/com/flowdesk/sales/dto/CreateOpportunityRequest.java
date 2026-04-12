package com.flowdesk.sales.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateOpportunityRequest(
        @NotNull UUID customerId,
        @Pattern(regexp = "PROSPECT|QUALIFIED|PROPOSAL|NEGOTIATION|CLOSED_WON|CLOSED_LOST") String stage,
        BigDecimal value
) {}
