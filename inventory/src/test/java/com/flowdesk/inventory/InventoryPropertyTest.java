package com.flowdesk.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.core.context.TenantContext;
import com.flowdesk.core.exception.BusinessRuleException;
import com.flowdesk.inventory.domain.Sku;
import com.flowdesk.inventory.domain.Stock;
import com.flowdesk.inventory.dto.AdjustStockRequest;
import com.flowdesk.inventory.dto.CreateSkuRequest;
import com.flowdesk.inventory.outbox.InventoryOutboxEvent;
import com.flowdesk.inventory.outbox.InventoryOutboxRepository;
import com.flowdesk.inventory.repository.PurchaseOrderRepository;
import com.flowdesk.inventory.repository.SkuRepository;
import com.flowdesk.inventory.repository.StockRepository;
import com.flowdesk.inventory.service.InventoryService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.flowdesk.core.lock.DistributedLockService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P19 (task 11.2): Low-stock events are published for any threshold-crossing transaction
 * Validates: Requirements 6.2
 */
class InventoryPropertyTest {

    private static final UUID TENANT = UUID.randomUUID();

    @AfterEach
    void cleanup() { TenantContext.clear(); }

    private InventoryService buildService(SkuRepository skuRepo, StockRepository stockRepo,
                                           InventoryOutboxRepository outboxRepo) {
        return new InventoryService(skuRepo, stockRepo,
                mock(PurchaseOrderRepository.class), outboxRepo, new ObjectMapper(),
                mock(DistributedLockService.class));
    }

    // ── Generators ────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<Integer> reorderThresholds() {
        return Arbitraries.integers().between(1, 100);
    }

    @Provide
    Arbitrary<Integer> quantitiesAtOrBelowThreshold() {
        return Arbitraries.integers().between(-50, 0); // delta that brings qty to <= threshold
    }

    // ── P19a: Low-stock event published when quantity <= threshold ────────────

    @Property(tries = 10)
    @Tag("Feature: saas-platform, Property 19: Low-stock events are published for any threshold-crossing transaction")
    void p19_lowStockEventPublishedWhenThresholdCrossed(
            @ForAll("reorderThresholds") int threshold,
            @ForAll @IntRange(min = 1, max = 50) int initialQty,
            @ForAll @IntRange(min = 1, max = 200) int deduction) {

        UUID skuId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        int resultingQty = initialQty - deduction;

        // Only test cases where result is at or below threshold
        Assume.that(resultingQty <= threshold);

        SkuRepository skuRepo = mock(SkuRepository.class);
        StockRepository stockRepo = mock(StockRepository.class);
        InventoryOutboxRepository outboxRepo = mock(InventoryOutboxRepository.class);

        Sku sku = new Sku();
        setId(sku, skuId);
        sku.setTenantId(TENANT);
        sku.setProductName("Widget");
        sku.setReorderThreshold(threshold);
        sku.setUnitCost(BigDecimal.ONE);

        Stock stock = new Stock();
        stock.setTenantId(TENANT);
        stock.setSkuId(skuId);
        stock.setWarehouseId(warehouseId);
        stock.setQuantityOnHand(initialQty);

        when(skuRepo.findByIdAndTenantId(skuId, TENANT)).thenReturn(Optional.of(sku));
        when(stockRepo.findBySkuIdAndWarehouseId(skuId, warehouseId)).thenReturn(Optional.of(stock));
        when(stockRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<InventoryOutboxEvent> publishedEvents = new ArrayList<>();
        when(outboxRepo.save(any())).thenAnswer(inv -> {
            publishedEvents.add(inv.getArgument(0));
            return inv.getArgument(0);
        });

        TenantContext.setTenantId(TENANT);
        InventoryService service = buildService(skuRepo, stockRepo, outboxRepo);

        Stock result = service.adjustStock(skuId, new AdjustStockRequest(warehouseId, -deduction));

        assertThat(result.getQuantityOnHand()).isEqualTo(resultingQty);
        assertThat(result.getQuantityOnHand()).isLessThanOrEqualTo(threshold);

        // Low-stock event must be published
        assertThat(publishedEvents).isNotEmpty();
        assertThat(publishedEvents.get(0).getEventType()).isEqualTo("inventory.low-stock");
    }

    // ── P19b: No low-stock event when quantity is above threshold ─────────────

    @Property(tries = 10)
    @Tag("Feature: saas-platform, Property 19: Low-stock events are published for any threshold-crossing transaction")
    void p19_noLowStockEventWhenAboveThreshold(
            @ForAll("reorderThresholds") int threshold,
            @ForAll @IntRange(min = 1, max = 50) int addition) {

        UUID skuId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        int initialQty = threshold + 1;
        int resultingQty = initialQty + addition; // always above threshold

        SkuRepository skuRepo = mock(SkuRepository.class);
        StockRepository stockRepo = mock(StockRepository.class);
        InventoryOutboxRepository outboxRepo = mock(InventoryOutboxRepository.class);

        Sku sku = new Sku();
        setId(sku, skuId);
        sku.setTenantId(TENANT);
        sku.setProductName("Widget");
        sku.setReorderThreshold(threshold);
        sku.setUnitCost(BigDecimal.ONE);

        Stock stock = new Stock();
        stock.setTenantId(TENANT);
        stock.setSkuId(skuId);
        stock.setWarehouseId(warehouseId);
        stock.setQuantityOnHand(initialQty);

        when(skuRepo.findByIdAndTenantId(skuId, TENANT)).thenReturn(Optional.of(sku));
        when(stockRepo.findBySkuIdAndWarehouseId(skuId, warehouseId)).thenReturn(Optional.of(stock));
        when(stockRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TenantContext.setTenantId(TENANT);
        InventoryService service = buildService(skuRepo, stockRepo, outboxRepo);

        Stock result = service.adjustStock(skuId, new AdjustStockRequest(warehouseId, addition));

        assertThat(result.getQuantityOnHand()).isGreaterThan(threshold);
        verify(outboxRepo, never()).save(any()); // no event published
    }

    // ── P19c: SKU not found rejects entire order ──────────────────────────────

    @Property(tries = 10)
    @Tag("Feature: saas-platform, Property 19: Low-stock events are published for any threshold-crossing transaction")
    void p19_nonExistentSkuRejectsStockAdjustment() {
        UUID skuId = UUID.randomUUID();
        SkuRepository skuRepo = mock(SkuRepository.class);
        when(skuRepo.findByIdAndTenantId(any(), any())).thenReturn(Optional.empty());

        TenantContext.setTenantId(TENANT);
        InventoryService service = buildService(skuRepo, mock(StockRepository.class),
                mock(InventoryOutboxRepository.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.adjustStock(skuId, new AdjustStockRequest(UUID.randomUUID(), 10)))
                .isInstanceOf(com.flowdesk.core.exception.ResourceNotFoundException.class);
    }

    // ── Reflection helper ─────────────────────────────────────────────────────

    private static void setId(Object entity, UUID id) {
        try {
            Class<?> clazz = entity.getClass();
            while (clazz != null) {
                try {
                    var f = clazz.getDeclaredField("id");
                    f.setAccessible(true);
                    f.set(entity, id);
                    return;
                } catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
