package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pl.cramber.assetstore.dto.UserResponse;
import pl.cramber.assetstore.entity.Asset;
import pl.cramber.assetstore.entity.AuditLog;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.AssetRepository;
import pl.cramber.assetstore.repository.AuditLogRepository;
import pl.cramber.assetstore.repository.UserLoginLogRepository;
import pl.cramber.assetstore.repository.UserRepository;
import pl.cramber.assetstore.service.PurchaseService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final PurchaseService purchaseService;
    private final AuditLogRepository auditLogRepository;
    private final UserLoginLogRepository userLoginLogRepository;

    private void logAction(OAuth2User principal, String action, String details) {
        if (principal == null) return;
        auditLogRepository.save(AuditLog.builder()
                .adminDiscordId(principal.getAttribute("id"))
                .action(action)
                .details(details)
                .build());
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> mappedUsers = userRepository.findAll()
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(mappedUsers);
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
        User savedUser = userRepository.save(user);

        logAction(principal, "UPDATE_ROLE", "Changed role of user " + user.getDiscordId() + " to " + newRole);
        return ResponseEntity.ok(mapToDto(savedUser));
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
        User savedUser = userRepository.save(user);

        logAction(principal, banned ? "BAN_USER" : "UNBAN_USER", "Toggled ban for user " + user.getDiscordId());
        return ResponseEntity.ok(mapToDto(savedUser));
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

    private UserResponse mapToDto(User user) {
        boolean isSuperadmin = "SUPERADMIN".equals(user.getRole());

        List<UserResponse.LoginLogDto> recentLogins = isSuperadmin
                ? List.of()
                : userLoginLogRepository.findTop5ByUserIdOrderByCreatedAtDesc(user.getId())
                  .stream()
                  .map(log -> new UserResponse.LoginLogDto(log.getIpAddress(), log.getCreatedAt()))
                  .toList();

        String safeEmail = isSuperadmin ? null : user.getEmail();

        return new UserResponse(
                user.getId(),
                user.getDiscordId(),
                user.getDiscordUsername(),
                user.getDiscordAvatarUrl(),
                user.getRobloxId(),
                user.getRobloxUsername(),
                safeEmail,
                user.getRole(),
                user.getBalance(),
                user.isBanned(),
                recentLogins
        );
    }
}