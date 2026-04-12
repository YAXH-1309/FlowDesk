package com.flowdesk.inventory.repository;

import com.flowdesk.inventory.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StockRepository extends JpaRepository<Stock, UUID> {
    Optional<Stock> findBySkuIdAndWarehouseId(UUID skuId, UUID warehouseId);
}
