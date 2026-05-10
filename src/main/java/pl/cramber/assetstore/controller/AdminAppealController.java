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
import org.springframework.web.bind.annotation.*;
import pl.cramber.assetstore.dto.AppealDto;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.UserRepository;
import pl.cramber.assetstore.service.AppealService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/appeals")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class AdminAppealController {

    private final AppealService appealService;
    private final UserRepository userRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Page<AppealDto>> getAllAppeals(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal OAuth2User principal) {

        User admin = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        if (!"SUPERADMIN".equals(admin.getRole()) && !"ADMIN".equals(admin.getRole()) && !"MODERATOR".equals(admin.getRole())) {
            return ResponseEntity.status(403).build();
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AppealDto> appeals = appealService.getAllAppeals(status, pageable);
        return ResponseEntity.ok(appeals);
    }

    @PostMapping("/{id}/resolve")
    @Transactional
    public ResponseEntity<?> resolveAppeal(
            @PathVariable UUID id,
            @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal OAuth2User principal) {

        User admin = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        if (!"SUPERADMIN".equals(admin.getRole())) {
            return ResponseEntity.status(403).build();
        }

        String action = payload.get("action");
        String reply = payload.get("reply");

        if (action == null || (!action.equals("ACCEPT") && !action.equals("REJECT"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid action"));
        }

        try {
            AppealDto appeal = appealService.resolveAppeal(id, admin, action, reply);
            return ResponseEntity.ok(appeal);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}