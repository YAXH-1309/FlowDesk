package com.flowdesk.inventory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.core.context.TenantContext;
import com.flowdesk.core.exception.BusinessRuleException;
import com.flowdesk.core.exception.ConflictException;
import com.flowdesk.core.exception.ResourceNotFoundException;
import com.flowdesk.core.kafka.events.LowStockEvent;
import com.flowdesk.core.lock.DistributedLockService;
import com.flowdesk.inventory.domain.PoLineItem;
import com.flowdesk.inventory.domain.PurchaseOrder;
import com.flowdesk.inventory.domain.Sku;
import com.flowdesk.inventory.domain.Stock;
import com.flowdesk.inventory.dto.AdjustStockRequest;
import com.flowdesk.inventory.dto.CreatePurchaseOrderRequest;
import com.flowdesk.inventory.dto.CreateSkuRequest;
import com.flowdesk.inventory.outbox.InventoryOutboxEvent;
import com.flowdesk.inventory.outbox.InventoryOutboxRepository;
import com.flowdesk.inventory.repository.PurchaseOrderRepository;
import com.flowdesk.inventory.repository.SkuRepository;
import com.flowdesk.inventory.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class InventoryService {

    private final SkuRepository skuRepo;
    private final StockRepository stockRepo;
    private final PurchaseOrderRepository poRepo;
    private final InventoryOutboxRepository outboxRepo;
    private final ObjectMapper objectMapper;
    private final DistributedLockService lockService;

    public InventoryService(SkuRepository skuRepo, StockRepository stockRepo,
                             PurchaseOrderRepository poRepo,
                             InventoryOutboxRepository outboxRepo,
                             ObjectMapper objectMapper,
                             DistributedLockService lockService) {
        this.skuRepo = skuRepo;
        this.stockRepo = stockRepo;
        this.poRepo = poRepo;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
        this.lockService = lockService;
    }

    // ── SKU ───────────────────────────────────────────────────────────────────

    @Transactional
    public Sku createSku(CreateSkuRequest req) {
        Sku sku = new Sku();
        sku.setTenantId(TenantContext.getTenantId());
        sku.setProductName(req.productName());
        sku.setReorderThreshold(req.reorderThreshold());
        sku.setUnitCost(req.unitCost());
        return skuRepo.save(sku);
    }

    @Transactional
    public Stock adjustStock(UUID skuId, AdjustStockRequest req) {
        UUID tenantId = TenantContext.getTenantId();
        String lockKey = "lock:stock:" + skuId + ":" + req.warehouseId();
        if (!lockService.tryLock(lockKey, 30)) {
            throw new ConflictException("Stock update already in progress for this SKU/warehouse");
        }
        try {
        Sku sku = skuRepo.findByIdAndTenantId(skuId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("SKU not found"));

        Stock stock = stockRepo.findBySkuIdAndWarehouseId(skuId, req.warehouseId())
                .orElseGet(() -> {
                    Stock s = new Stock();
                    s.setTenantId(tenantId);
                    s.setSkuId(skuId);
                    s.setWarehouseId(req.warehouseId());
                    return s;
                });

        stock.setQuantityOnHand(stock.getQuantityOnHand() + req.quantityDelta());
        Stock saved = stockRepo.save(stock);

        // Publish low-stock event if threshold crossed
        if (saved.getQuantityOnHand() <= sku.getReorderThreshold()) {
            LowStockEvent lowStockEvent = new LowStockEvent();
            lowStockEvent.setSkuId(skuId);
            lowStockEvent.setTenantId(tenantId);
            lowStockEvent.setWarehouseId(req.warehouseId());
            lowStockEvent.setQuantityOnHand(saved.getQuantityOnHand());
            lowStockEvent.setReorderThreshold(sku.getReorderThreshold());
            publishOutbox("Stock", skuId, "inventory.low-stock", lowStockEvent);
        }
        return saved;
        } finally {
            lockService.unlock(lockKey);
        }
    }

    // ── Purchase Orders ───────────────────────────────────────────────────────

    @Transactional
    public PurchaseOrder createPurchaseOrder(CreatePurchaseOrderRequest req) {
        UUID tenantId = TenantContext.getTenantId();

        // Validate all SKUs exist
        for (var line : req.lineItems()) {
            skuRepo.findByIdAndTenantId(line.skuId(), tenantId)
                    .orElseThrow(() -> new BusinessRuleException(
                            "SKU " + line.skuId() + " not found; order rejected"));
        }

        PurchaseOrder po = new PurchaseOrder();
        po.setTenantId(tenantId);
        po.setSupplierId(req.supplierId());

        List<PoLineItem> items = req.lineItems().stream().map(l -> {
            PoLineItem item = new PoLineItem();
            item.setSkuId(l.skuId());
            item.setQuantity(l.quantity());
            item.setUnitCost(l.unitCost());
            return item;
        }).toList();
        po.setLineItems(items);

        return poRepo.save(po);
    }

    @Transactional
    public PurchaseOrder receivePurchaseOrder(UUID poId) {
        UUID tenantId = TenantContext.getTenantId();
        PurchaseOrder po = poRepo.findByIdAndTenantId(poId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found"));

        // Update stock quantities in same transaction
        for (PoLineItem line : po.getLineItems()) {
            Stock stock = stockRepo.findBySkuIdAndWarehouseId(line.getSkuId(), tenantId)
                    .orElseGet(() -> {
                        Stock s = new Stock();
                        s.setTenantId(tenantId);
                        s.setSkuId(line.getSkuId());
                        s.setWarehouseId(tenantId); // default warehouse = tenant
                        return s;
                    });
            stock.setQuantityOnHand(stock.getQuantityOnHand() + line.getQuantity());
            stockRepo.save(stock);
        }

        po.setStatus("RECEIVED");
        return poRepo.save(po);
    }

    // ── Outbox helper ─────────────────────────────────────────────────────────

    private void publishOutbox(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        try {
            InventoryOutboxEvent event = new InventoryOutboxEvent();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            outboxRepo.save(event);
        } catch (Exception ignored) {}
    }
}
