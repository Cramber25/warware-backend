package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.AuditLogRepository;
import pl.cramber.assetstore.repository.OrderRepository;
import pl.cramber.assetstore.repository.UserRepository;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminStatsController {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    @GetMapping("/sales")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getSalesHistory(@AuthenticationPrincipal OAuth2User principal) {
        String discordId = principal.getAttribute("id");
        User admin = userRepository.findByDiscordId(discordId).orElseThrow();

        var orders = "SUPERADMIN".equals(admin.getRole())
                ? orderRepository.findAllByOrderTypeOrderByCreatedAtDesc("PURCHASE")
                : orderRepository.findByAssetCreatorIdAndOrderTypeOrderByCreatedAtDesc(admin.getId(), "PURCHASE");

        var result = orders.stream().map(o -> Map.of(
                "orderId", o.getId(),
                "status", o.getStatus(),
                "createdAt", o.getCreatedAt(),
                "buyer", o.getUser().getDiscordUsername() != null ? o.getUser().getDiscordUsername() : o.getUser().getDiscordId(),
                "assetTitle", o.getAsset().getTitle(),
                "price", o.getPurchasePrice(),
                "promoCode", o.getPromoCode() != null ? o.getPromoCode().getCode() : null
        )).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/audit")
    public ResponseEntity<?> getAuditLogs() {
        return ResponseEntity.ok(auditLogRepository.findAllByOrderByCreatedAtDesc());
    }
}