package com.paypus.settlement;

import com.paypus.common.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
public class SettlementController {
    private final SettlementService settlementService;
    private final SettlementLineItemRepository settlementLineItemRepository;

    public SettlementController(
            SettlementService settlementService,
            SettlementLineItemRepository settlementLineItemRepository
    ){
        this.settlementService = settlementService;
        this.settlementLineItemRepository = settlementLineItemRepository;
    }

    @PostMapping("/v1/settlements")
    public ResponseEntity<SettlementBatchResponse> createSettlement(@RequestParam String currency){
        UUID tenantId = TenantContext.getTenantId();

        SettlementBatch batch = settlementService.createSettlementBatch(tenantId, currency);

        List<UUID> paymentIds = settlementLineItemRepository.findBySettlementBatchId(batch.getId()).stream()
                .map(li -> li.getPayment().getId())
                .collect(Collectors.toList());

        SettlementBatchResponse response = new SettlementBatchResponse(
                batch.getId(),
                batch.getTenant().getId(),
                batch.getStatus(),
                batch.getTotalAmount(),
                batch.getCurrency(),
                paymentIds,
                batch.getCreatedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
