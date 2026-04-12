package com.flowdesk.inventory.domain;

import com.flowdesk.core.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "skus", schema = "inventory_schema")
public class Sku extends BaseEntity {

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "reorder_threshold", nullable = false)
    private int reorderThreshold = 0;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 4)
    private BigDecimal unitCost;

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public int getReorderThreshold() { return reorderThreshold; }
    public void setReorderThreshold(int reorderThreshold) { this.reorderThreshold = reorderThreshold; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
}
