package com.paypus.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypus.common.TenantContext;
import com.paypus.idempotency.IdempotencyResult;
import com.paypus.idempotency.IdempotencyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class RefundController {

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public RefundController(
            PaymentService paymentService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper
    ) throws Exception {
        this.paymentService = paymentService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/v1/payments/{id}/refund", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> refundPayment(
            @PathVariable("id") UUID paymentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) throws Exception {
        UUID tenantId = TenantContext.getTenantId();
        String requestBody = "refund:" + paymentId;

        IdempotencyResult idempotencyResult = idempotencyService.begin(tenantId, idempotencyKey, requestBody);

        if (idempotencyResult.isReplay()) {
            return ResponseEntity
                    .status(idempotencyResult.responseStatus())
                    .body(idempotencyResult.responseBody());
        }

        RefundResult refundResult;
        try {
            refundResult = paymentService.refundPayment(tenantId, paymentId);
        }catch (RuntimeException e){
            idempotencyService.fail(tenantId, idempotencyKey);
            throw e;
        }

        RefundResponse response = new RefundResponse(
                refundResult.payment().getId(),
                refundResult.stripeRefundId(),
                refundResult.payment().getAmount(),
                refundResult.payment().getCurrency(),
                refundResult.payment().getStatus(),
                refundResult.payment().getUpdatedAt()
        );

        String responseBody = objectMapper.writeValueAsString(response);
        idempotencyService.complete(tenantId, idempotencyKey, HttpStatus.OK.value(), responseBody);

        return ResponseEntity.ok(responseBody);
    }
}