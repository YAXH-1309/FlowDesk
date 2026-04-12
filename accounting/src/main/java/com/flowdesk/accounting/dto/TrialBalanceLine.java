package com.flowdesk.accounting.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TrialBalanceLine(UUID accountId, String code, String name, String type, BigDecimal balance) {}
