package com.paypus.payments;

import com.paypus.settlement.SettlementWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Payout;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StripeWebhookController {

    private final PaymentWebhookService paymentWebhookService;
    private final SettlementWebhookService settlementWebhookService;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    public StripeWebhookController(
            PaymentWebhookService paymentWebhookService,
            SettlementWebhookService settlementWebhookService
    ) {
        this.paymentWebhookService = paymentWebhookService;
        this.settlementWebhookService = settlementWebhookService;
    }

    @PostMapping("/v1/webhooks/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestHeader("Stripe-Signature") String signatureHeader,
            @RequestBody String payload
    ) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        switch (event.getType()) {
            case "payment_intent.succeeded" -> {
                PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                        .getObject()
                        .orElseThrow(() -> new IllegalStateException("Could not deserialize PaymentIntent from event"));
                paymentWebhookService.handlePaymentSucceeded(paymentIntent);
            }
            case "payout.paid" -> {
                Payout payout = (Payout) event.getDataObjectDeserializer()
                        .getObject()
                        .orElseThrow(() -> new IllegalStateException("Could not deserialize Payout from event"));
                settlementWebhookService.handlePayoutPaid(payout);
            }
            case "payout.failed" -> {
                Payout payout = (Payout) event.getDataObjectDeserializer()
                        .getObject()
                        .orElseThrow(() -> new IllegalStateException("Could not deserialize Payout from event"));
                settlementWebhookService.handlePayoutFailed(payout);
            }
            default -> {
            }
        }

        return ResponseEntity.ok("received");
    }
}