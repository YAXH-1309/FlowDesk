package com.flowdesk.accounting.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "budgets", schema = "accounting_schema")
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "cost_center", nullable = false, length = 100)
    private String costCenter;

    @Column(name = "fiscal_period", nullable = false, length = 20)
    private String fiscalPeriod;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal allocated = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal committed = BigDecimal.ZERO;

    @Column(name = "actual_spend", nullable = false, precision = 15, scale = 2)
    private BigDecimal actualSpend = BigDecimal.ZERO;

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getCostCenter() { return costCenter; }
    public void setCostCenter(String costCenter) { this.costCenter = costCenter; }
    public String getFiscalPeriod() { return fiscalPeriod; }
    public void setFiscalPeriod(String fiscalPeriod) { this.fiscalPeriod = fiscalPeriod; }
    public BigDecimal getAllocated() { return allocated; }
    public void setAllocated(BigDecimal allocated) { this.allocated = allocated; }
    public BigDecimal getCommitted() { return committed; }
    public void setCommitted(BigDecimal committed) { this.committed = committed; }
    public BigDecimal getActualSpend() { return actualSpend; }
    public void setActualSpend(BigDecimal actualSpend) { this.actualSpend = actualSpend; }
}
