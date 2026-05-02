package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.cramber.assetstore.repository.BlacklistEntryRepository;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/roblox/blacklist")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class BlacklistWebhookController {

    private final BlacklistEntryRepository blacklistEntryRepository;

    @GetMapping("/{robloxId}")
    public ResponseEntity<?> checkBlacklist(@PathVariable String robloxId) {
        boolean isBlacklisted = blacklistEntryRepository.existsByRobloxIdAndIsActiveTrue(robloxId);
        return ResponseEntity.ok(Map.of("blacklisted", isBlacklisted));
    }
}