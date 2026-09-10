package com.paypus.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypus.common.TenantContext;
import com.paypus.idempotency.IdempotencyResult;
import com.paypus.idempotency.IdempotencyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public PaymentController(
            PaymentService paymentService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper
    ) {
        this.paymentService = paymentService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/v1/payments", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody String rawBody
    ) throws Exception {
        var tenantId = TenantContext.getTenantId();

        IdempotencyResult idempotencyResult = idempotencyService.begin(tenantId, idempotencyKey, rawBody);

        if (idempotencyResult.isReplay()) {
            return ResponseEntity
                    .status(idempotencyResult.responseStatus())
                    .body(idempotencyResult.responseBody());
        }

        CreatePaymentRequest request = objectMapper.readValue(rawBody, CreatePaymentRequest.class);

        PaymentCreationResult result;
        try {
            result = paymentService.createPayment(tenantId, request);
        }catch (RuntimeException e){
            idempotencyService.fail(tenantId, idempotencyKey);
            throw e;
        }

        PaymentResponse response = new PaymentResponse(
                result.payment().getId(),
                result.stripeClientSecret(),
                result.payment().getAmount(),
                result.payment().getCurrency(),
                result.payment().getStatus(),
                result.payment().getCreatedAt()
        );

        String responseBody = objectMapper.writeValueAsString(response);

        idempotencyService.complete(tenantId, idempotencyKey, HttpStatus.CREATED.value(), responseBody);

        return ResponseEntity.status(HttpStatus.CREATED).body(responseBody);
    }
}