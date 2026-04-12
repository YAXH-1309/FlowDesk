package com.flowdesk.reporting.dto;

import jakarta.validation.constraints.NotBlank;

public record DefineReportRequest(
        @NotBlank String name,
        @NotBlank String sourceModule,
        String filterCriteria,
        String[] groupingFields,
        String[] outputColumns
) {}
