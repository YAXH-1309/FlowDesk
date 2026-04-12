package com.flowdesk.accounting.dto;

import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PostJournalEntryRequest(
        String description,
        @NotEmpty List<LineRequest> lines
) {
    public record LineRequest(UUID accountId, BigDecimal amount, String description) {}
}
