package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.cramber.assetstore.repository.UserRepository;
import pl.cramber.assetstore.service.WalletService;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/roblox")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class RobloxWebhookController {

    private final WalletService walletService;
    private final UserRepository userRepository;

    @Value("${roblox.webhook.key}")
    private String secretKey;

    @GetMapping("/check-user/{robloxId}")
    public ResponseEntity<String> checkUser(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @PathVariable String robloxId) {

        if (!secretKey.equals(apiKey)) {
            return ResponseEntity.status(403).build();
        }

        return userRepository.findByRobloxId(robloxId)
                .map(user -> ResponseEntity.ok("FOUND"))
                .orElse(ResponseEntity.ok("NOT_FOUND"));
    }

    @PostMapping("/deposit")
    public ResponseEntity<Void> deposit(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestBody Map<String, Object> payload) {

        if (!secretKey.equals(apiKey)) {
            return ResponseEntity.status(403).build();
        }

        String robloxId = payload.get("robloxId").toString();
        Integer amount = (Integer) payload.get("amount");

        if (amount == null || amount <= 0) {
            return ResponseEntity.badRequest().build();
        }

        walletService.addFunds(robloxId, amount);

        return ResponseEntity.ok().build();
    }
}