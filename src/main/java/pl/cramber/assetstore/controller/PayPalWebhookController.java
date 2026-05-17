package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.cramber.assetstore.service.PurchaseService;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/paypal")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class PayPalWebhookController {

    private final PurchaseService purchaseService;

    @PostMapping
    public ResponseEntity<Void> handleWebhook(@RequestBody Map<String, Object> payload) {
        String eventType = (String) payload.get("event_type");

        if ("CHECKOUT.ORDER.APPROVED".equals(eventType)) {
            Map<String, Object> resource = (Map<String, Object>) payload.get("resource");
            String orderId = (String) resource.get("id");

            try {
                purchaseService.captureOrderFromWebhook(orderId);
            } catch (Exception ignored) {
            }
        }

        return ResponseEntity.ok().build();
    }
}