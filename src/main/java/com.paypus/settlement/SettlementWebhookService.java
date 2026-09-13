package com.paypus.settlement;

import com.stripe.model.Payout;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class SettlementWebhookService {

    private final SettlementBatchRepository settlementBatchRepository;

    public SettlementWebhookService(SettlementBatchRepository settlementBatchRepository) {
        this.settlementBatchRepository = settlementBatchRepository;
    }

    @Transactional
    public void handlePayoutPaid(Payout stripePayout) {
        SettlementBatch batch = findBatchByPayoutId(stripePayout.getId());

        if (batch.getStatus() == SettlementStatus.PAID_OUT) {
            return;
        }

        batch.setStatus(SettlementStatus.PAID_OUT);
        batch.setUpdatedAt(OffsetDateTime.now());
        settlementBatchRepository.save(batch);
    }

    @Transactional
    public void handlePayoutFailed(Payout stripePayout) {
        SettlementBatch batch = findBatchByPayoutId(stripePayout.getId());

        if (batch.getStatus() == SettlementStatus.FAILED) {
            return;
        }

        batch.setStatus(SettlementStatus.FAILED);
        batch.setFailureReason(stripePayout.getFailureCode());
        batch.setUpdatedAt(OffsetDateTime.now());
        settlementBatchRepository.save(batch);
    }

    private SettlementBatch findBatchByPayoutId(String stripePayoutId) {
        Optional<SettlementBatch> batch = settlementBatchRepository.findAll().stream()
                .filter(b -> stripePayoutId.equals(b.getStripePayoutId()))
                .findFirst();

        return batch.orElseThrow(() ->
                new IllegalStateException("No settlement batch found for Stripe payout: " + stripePayoutId)
        );
    }
}