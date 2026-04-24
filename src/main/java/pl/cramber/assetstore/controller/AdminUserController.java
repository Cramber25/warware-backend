package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pl.cramber.assetstore.entity.Asset;
import pl.cramber.assetstore.entity.AuditLog;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.AssetRepository;
import pl.cramber.assetstore.repository.AuditLogRepository;
import pl.cramber.assetstore.repository.UserRepository;
import pl.cramber.assetstore.service.PurchaseService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final PurchaseService purchaseService;
    private final AuditLogRepository auditLogRepository;

    private void logAction(OAuth2User principal, String action, String details) {
        if (principal == null) return;
        auditLogRepository.save(AuditLog.builder()
                .adminDiscordId(principal.getAttribute("id"))
                .action(action)
                .details(details)
                .build());
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @PutMapping("/{userId}/role")
    @Transactional
    public ResponseEntity<?> updateRole(@PathVariable UUID userId, @RequestBody Map<String, String> payload, @AuthenticationPrincipal OAuth2User principal) {
        User user = userRepository.findById(userId).orElseThrow();
        String newRole = payload.get("role");

        if ("SUPERADMIN".equals(user.getRole())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot modify a Superadmin."));
        }
        if ("SUPERADMIN".equals(newRole)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot grant Superadmin role."));
        }

        user.setRole(newRole);
        userRepository.save(user);
        logAction(principal, "UPDATE_ROLE", "Changed role of user " + user.getDiscordId() + " to " + newRole);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{userId}/ban")
    @Transactional
    public ResponseEntity<?> toggleBan(@PathVariable UUID userId, @RequestBody Map<String, Boolean> payload, @AuthenticationPrincipal OAuth2User principal) {
        User user = userRepository.findById(userId).orElseThrow();
        if ("SUPERADMIN".equals(user.getRole())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot ban a Superadmin."));
        }

        boolean banned = payload.get("banned");
        user.setBanned(banned);
        userRepository.save(user);
        logAction(principal, banned ? "BAN_USER" : "UNBAN_USER", "Toggled ban for user " + user.getDiscordId());
        return ResponseEntity.ok(user);
    }

    @PostMapping("/blacklist")
    @Transactional
    public ResponseEntity<?> blacklistByPlatformId(@RequestBody Map<String, String> payload, @AuthenticationPrincipal OAuth2User principal) {
        String platform = payload.get("platform");
        String id = payload.get("id");

        Runnable banLogic = () -> {
            if ("DISCORD".equals(platform)) {
                userRepository.findByDiscordId(id).ifPresentOrElse(u -> {
                    if (!"SUPERADMIN".equals(u.getRole())) { u.setBanned(true); userRepository.save(u); }
                }, () -> userRepository.save(User.builder().discordId(id).banned(true).build()));
            } else if ("ROBLOX".equals(platform)) {
                userRepository.findByRobloxId(id).ifPresentOrElse(u -> {
                    if (!"SUPERADMIN".equals(u.getRole())) { u.setBanned(true); userRepository.save(u); }
                }, () -> userRepository.save(User.builder().discordId("DUMMY_" + UUID.randomUUID()).robloxId(id).banned(true).build()));
            }
        };
        banLogic.run();
        logAction(principal, "BLACKLIST", "Blacklisted " + platform + " ID: " + id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/grant")
    public ResponseEntity<?> grantAsset(@PathVariable UUID userId, @RequestBody Map<String, String> payload, @AuthenticationPrincipal OAuth2User principal) {
        UUID assetId = UUID.fromString(payload.get("assetId"));
        purchaseService.grantAccess(userId, assetId);

        User target = userRepository.findById(userId).orElseThrow();
        Asset asset = assetRepository.findById(assetId).orElseThrow();

        String details = String.format("{\"assetId\":\"%s\", \"assetTitle\":\"%s\", \"discordId\":\"%s\", \"discordUsername\":\"%s\"}",
                asset.getId(), asset.getTitle().replace("\"", "\\\""), target.getDiscordId(), target.getDiscordUsername() != null ? target.getDiscordUsername().replace("\"", "\\\"") : target.getDiscordId());

        logAction(principal, "GRANT_ASSET", details);
        return ResponseEntity.ok().build();
    }
}