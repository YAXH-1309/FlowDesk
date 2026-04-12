package com.flowdesk.inventory.controller;

import com.flowdesk.inventory.domain.PurchaseOrder;
import com.flowdesk.inventory.domain.Sku;
import com.flowdesk.inventory.domain.Stock;
import com.flowdesk.inventory.dto.AdjustStockRequest;
import com.flowdesk.inventory.dto.CreatePurchaseOrderRequest;
import com.flowdesk.inventory.dto.CreateSkuRequest;
import com.flowdesk.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/skus")
    @ResponseStatus(HttpStatus.CREATED)
    public Sku createSku(@Valid @RequestBody CreateSkuRequest req) {
        return inventoryService.createSku(req);
    }

    @PutMapping("/skus/{skuId}/stock")
    public Stock adjustStock(@PathVariable UUID skuId,
                             @Valid @RequestBody AdjustStockRequest req) {
        return inventoryService.adjustStock(skuId, req);
    }

    @PostMapping("/purchase-orders")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrder createPurchaseOrder(@Valid @RequestBody CreatePurchaseOrderRequest req) {
        return inventoryService.createPurchaseOrder(req);
    }

    @PutMapping("/purchase-orders/{poId}/receive")
    public PurchaseOrder receivePurchaseOrder(@PathVariable UUID poId) {
        return inventoryService.receivePurchaseOrder(poId);
    }
}
