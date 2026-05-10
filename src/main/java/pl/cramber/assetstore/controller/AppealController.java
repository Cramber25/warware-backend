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
@RequestMapping("/api/appeals")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class AppealController {

    private final AppealService appealService;
    private final UserRepository userRepository;

    @GetMapping("/my")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMyAppeals(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        User user = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<AppealDto> appeals = appealService.getUserAppeals(user.getId(), pageable);
        return ResponseEntity.ok(appeals);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> submitAppeal(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody Map<String, String> payload) {

        User user = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        String type = payload.get("appealType");
        String content = payload.get("content");
        UUID referenceId = payload.containsKey("referenceId") ? UUID.fromString(payload.get("referenceId")) : null;

        if (content == null || content.trim().isEmpty() || type == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields"));
        }

        try {
            AppealDto appeal = appealService.submitAppeal(user, type, referenceId, content);
            return ResponseEntity.ok(appeal);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}