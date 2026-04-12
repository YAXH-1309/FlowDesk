package com.flowdesk.inventory.domain;

import com.flowdesk.core.domain.BaseEntity;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders", schema = "inventory_schema")
public class PurchaseOrder extends BaseEntity {

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "po_id")
    private List<PoLineItem> lineItems = new ArrayList<>();

    public UUID getSupplierId() { return supplierId; }
    public void setSupplierId(UUID supplierId) { this.supplierId = supplierId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<PoLineItem> getLineItems() { return lineItems; }
    public void setLineItems(List<PoLineItem> lineItems) { this.lineItems = lineItems; }
}
