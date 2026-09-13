package com.paypus.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SettlementLineItemRepository extends JpaRepository<SettlementLineItem, UUID> {
    List<SettlementLineItem> findBySettlementBatchId(UUID SettlementBatchId);
}
