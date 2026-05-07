package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pl.cramber.assetstore.dto.TicketDto;
import pl.cramber.assetstore.dto.TicketMessageDto;
import pl.cramber.assetstore.entity.Ticket;
import pl.cramber.assetstore.entity.TicketMessage;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.TicketMessageRepository;
import pl.cramber.assetstore.repository.TicketRepository;
import pl.cramber.assetstore.repository.UserRepository;
import pl.cramber.assetstore.service.TicketService;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class TicketController {

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository ticketMessageRepository;
    private final UserRepository userRepository;
    private final TicketService ticketService;

    @GetMapping("/my")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMyTickets(@AuthenticationPrincipal OAuth2User principal) {
        User user = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        var tickets = ticketRepository.findByOrderUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(TicketDto::fromEntity)
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
                .map(TicketMessageDto::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("ticket", TicketDto.fromEntity(ticket), "messages", messages));
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

        TicketMessage saved = ticketService.sendMessage(ticket, user, payload.get("content"));
        return ResponseEntity.ok(TicketMessageDto.fromEntity(saved));
    }
}