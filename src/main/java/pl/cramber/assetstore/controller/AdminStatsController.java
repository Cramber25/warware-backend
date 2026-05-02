package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.cramber.assetstore.dto.SaleDto;
import pl.cramber.assetstore.entity.AuditLog;
import pl.cramber.assetstore.entity.Order;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.AuditLogRepository;
import pl.cramber.assetstore.repository.OrderRepository;
import pl.cramber.assetstore.repository.UserRepository;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class AdminStatsController {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    @GetMapping("/sales")
    @Transactional(readOnly = true)
    public ResponseEntity<Page<SaleDto>> getSalesHistory(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @AuthenticationPrincipal OAuth2User principal) {

        String discordId = principal.getAttribute("id");
        User admin = userRepository.findByDiscordId(discordId).orElseThrow();

        String safeSearch = search == null ? "" : search;
        Sort.Direction direction = sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Page<Order> orders = "SUPERADMIN".equals(admin.getRole())
                ? orderRepository.searchSales("PURCHASE", safeSearch, pageable)
                : orderRepository.searchCreatorSales(admin.getId(), "PURCHASE", safeSearch, pageable);

        Page<SaleDto> result = orders.map(o -> new SaleDto(
                o.getId(),
                o.getStatus(),
                o.getCreatedAt(),
                o.getUser().getDiscordUsername() != null ? o.getUser().getDiscordUsername() : o.getUser().getDiscordId(),
                o.getAsset().getTitle(),
                o.getPurchasePrice(),
                o.getPromoCode() != null ? o.getPromoCode().getCode() : null
        ));

        return ResponseEntity.ok(result);
    }

    @GetMapping("/audit")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        String safeSearch = search == null ? "" : search;
        Sort.Direction direction = sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Page<AuditLog> logs = auditLogRepository.searchLogs(safeSearch, pageable);
        return ResponseEntity.ok(logs);
    }
}