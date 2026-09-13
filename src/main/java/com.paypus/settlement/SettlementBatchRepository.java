package com.paypus.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SettlementBatchRepository extends JpaRepository<SettlementBatch, UUID> {

    Optional<SettlementBatch> findByStripePayoutId(String stripePayoutId);
}