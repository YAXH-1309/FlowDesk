package com.flowdesk.hr.dto;

import java.util.List;
import java.util.UUID;

public record PayrollReport(
        UUID runId,
        List<PayrollLineItem> lines,
        List<UUID> skippedEmployeeIds
) {}
