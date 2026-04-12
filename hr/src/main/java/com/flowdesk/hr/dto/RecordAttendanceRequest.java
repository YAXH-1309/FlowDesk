package com.flowdesk.hr.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RecordAttendanceRequest(
        @NotNull UUID employeeId,
        @NotNull LocalDate date,
        OffsetDateTime checkIn,
        OffsetDateTime checkOut,
        @NotNull @Pattern(regexp = "PRESENT|ABSENT|LATE|ON_LEAVE") String status
) {}
