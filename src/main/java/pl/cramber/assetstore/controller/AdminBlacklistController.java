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
import pl.cramber.assetstore.entity.BlacklistEntry;
import pl.cramber.assetstore.repository.BlacklistEntryRepository;
import pl.cramber.assetstore.repository.UserRepository;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/blacklists")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class AdminBlacklistController {

    private final BlacklistEntryRepository blacklistEntryRepository;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<Page<BlacklistEntry>> getBlacklists(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @AuthenticationPrincipal OAuth2User principal) {

        String authorities = principal.getAuthorities().toString();
        if (!authorities.contains("ROLE_SUPERADMIN")) {
            return ResponseEntity.status(403).build();
        }

        String safeSearch = search == null ? "" : search;
        Sort.Direction direction = sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        return ResponseEntity.ok(blacklistEntryRepository.searchBlacklists(safeSearch, pageable));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<BlacklistEntry> createBlacklist(@RequestBody Map<String, String> payload, @AuthenticationPrincipal OAuth2User principal) {
        String authorities = principal.getAuthorities().toString();
        if (!authorities.contains("ROLE_SUPERADMIN")) {
            return ResponseEntity.status(403).build();
        }

        String discordId = payload.get("discordId");
        String robloxId = payload.get("robloxId");

        BlacklistEntry entry = BlacklistEntry.builder()
                .discordId(discordId)
                .robloxId(robloxId)
                .robloxUsername(payload.get("robloxUsername"))
                .reason(payload.get("reason"))
                .markdownInfo(payload.get("markdownInfo"))
                .adminDiscordId(principal.getAttribute("id"))
                .isActive(true)
                .build();

        if (discordId != null && !discordId.isEmpty()) {
            userRepository.findByDiscordId(discordId).ifPresent(u -> {
                u.setBanned(true);
                userRepository.save(u);
            });
        } else if (robloxId != null && !robloxId.isEmpty()) {
            userRepository.findByRobloxId(robloxId).ifPresent(u -> {
                u.setBanned(true);
                userRepository.save(u);
            });
        }

        return ResponseEntity.ok(blacklistEntryRepository.save(entry));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BlacklistEntry> updateBlacklistInfo(@PathVariable UUID id, @RequestBody Map<String, String> payload, @AuthenticationPrincipal OAuth2User principal) {
        String authorities = principal.getAuthorities().toString();
        if (!authorities.contains("ROLE_SUPERADMIN")) {
            return ResponseEntity.status(403).build();
        }

        BlacklistEntry entry = blacklistEntryRepository.findById(id).orElseThrow();
        entry.setMarkdownInfo(payload.get("markdownInfo"));

        return ResponseEntity.ok(blacklistEntryRepository.save(entry));
    }
}