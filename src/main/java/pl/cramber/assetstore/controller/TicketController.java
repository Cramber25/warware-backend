package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pl.cramber.assetstore.entity.Ticket;
import pl.cramber.assetstore.entity.TicketMessage;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.TicketMessageRepository;
import pl.cramber.assetstore.repository.TicketRepository;
import pl.cramber.assetstore.repository.UserRepository;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository ticketMessageRepository;
    private final UserRepository userRepository;

    @GetMapping("/my")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMyTickets(@AuthenticationPrincipal OAuth2User principal) {
        User user = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        var tickets = ticketRepository.findByOrderUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::mapTicketToDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/{ticketId}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getTicketDetails(@PathVariable UUID ticketId, @AuthenticationPrincipal OAuth2User principal) {
        User user = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();

        if (!ticket.getOrder().getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }
        var messages = ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(this::mapMessageToDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("ticket", mapTicketToDto(ticket), "messages", messages));
    }

    @PostMapping("/{ticketId}/messages")
    @Transactional
    public ResponseEntity<?> sendMessage(@PathVariable UUID ticketId, @AuthenticationPrincipal OAuth2User principal, @RequestBody Map<String, String> payload) {
        User user = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();

        if (!ticket.getOrder().getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }
        if ("CLOSED".equals(ticket.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ticket is closed"));
        }

        TicketMessage msg = TicketMessage.builder()
                .ticket(ticket)
                .sender(user)
                .content(payload.get("content"))
                .build();

        TicketMessage saved = ticketMessageRepository.save(msg);
        return ResponseEntity.ok(mapMessageToDto(saved));
    }

    private Map<String, Object> mapTicketToDto(Ticket t) {
        return Map.of(
                "id", t.getId(),
                "status", t.getStatus(),
                "createdAt", t.getCreatedAt() != null ? t.getCreatedAt() : ZonedDateTime.now(),
                "order", Map.of(
                        "id", t.getOrder().getId(),
                        "asset", Map.of(
                                "title", t.getOrder().getAsset().getTitle(),
                                "price", t.getOrder().getAsset().getPrice(),
                                "thumbnailUrl", t.getOrder().getAsset().getThumbnailUrl() != null ? t.getOrder().getAsset().getThumbnailUrl() : ""
                        ),
                        "user", Map.of(
                                "id", t.getOrder().getUser().getId(),
                                "discordUsername", t.getOrder().getUser().getDiscordUsername() != null ? t.getOrder().getUser().getDiscordUsername() : t.getOrder().getUser().getDiscordId(),
                                "discordId", t.getOrder().getUser().getDiscordId(),
                                "robloxUsername", t.getOrder().getUser().getRobloxUsername() != null ? t.getOrder().getUser().getRobloxUsername() : "",
                                "robloxId", t.getOrder().getUser().getRobloxId() != null ? t.getOrder().getUser().getRobloxId() : ""
                        )
                )
        );
    }

    private Map<String, Object> mapMessageToDto(TicketMessage m) {
        return Map.of(
                "id", m.getId(),
                "content", m.getContent() != null ? m.getContent() : "",
                "createdAt", m.getCreatedAt() != null ? m.getCreatedAt() : ZonedDateTime.now(),
                "sender", Map.of(
                        "id", m.getSender().getId(),
                        "discordUsername", m.getSender().getDiscordUsername() != null ? m.getSender().getDiscordUsername() : m.getSender().getDiscordId()
                )
        );
    }
}