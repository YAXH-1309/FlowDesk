package com.flowdesk.sales.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record CreateInteractionRequest(
        UUID customerId,
        UUID opportunityId,
        @NotNull @Pattern(regexp = "CALL|EMAIL|MEETING") String type,
        String notes
) {}
