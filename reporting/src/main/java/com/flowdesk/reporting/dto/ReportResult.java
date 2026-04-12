package com.flowdesk.reporting.dto;

import java.util.List;
import java.util.Map;

public record ReportResult(
        List<Map<String, Object>> rows,
        int totalRows,
        String nextCursor  // null when no more pages
) {}
