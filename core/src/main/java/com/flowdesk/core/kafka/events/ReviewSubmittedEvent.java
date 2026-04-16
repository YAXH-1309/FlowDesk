package com.flowdesk.core.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published to {@code hr.review.submitted} when a performance review is submitted.
 * Schema version: 1
 */
public class ReviewSubmittedEvent extends KafkaEvent {

    @JsonProperty("reviewId")
    private UUID reviewId;

    @JsonProperty("tenantId")
    private UUID tenantId;

    @JsonProperty("employeeId")
    private UUID employeeId;

    @JsonProperty("reviewerId")
    private UUID reviewerId;

    @JsonProperty("reviewPeriod")
    private String reviewPeriod;

    @JsonProperty("rating")
    private BigDecimal rating;

    @JsonProperty("submittedAt")
    private OffsetDateTime submittedAt;

    public ReviewSubmittedEvent() {}

    public UUID getReviewId() { return reviewId; }
    public void setReviewId(UUID reviewId) { this.reviewId = reviewId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public UUID getReviewerId() { return reviewerId; }
    public void setReviewerId(UUID reviewerId) { this.reviewerId = reviewerId; }

    public String getReviewPeriod() { return reviewPeriod; }
    public void setReviewPeriod(String reviewPeriod) { this.reviewPeriod = reviewPeriod; }

    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }

    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
}
