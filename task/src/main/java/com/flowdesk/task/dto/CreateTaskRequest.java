package com.flowdesk.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTaskRequest(
        @NotBlank @Size(max = 500) String title,
        String description,
        @Pattern(regexp = "TODO|IN_PROGRESS|REVIEW|DONE") String status
) {}
