package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pl.cramber.assetstore.entity.*;
import pl.cramber.assetstore.repository.*;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/tickets")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class AdminTicketController {

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository ticketMessageRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;
    private final AuditLogRepository auditLogRepository;

    private boolean hasAccess(User admin, Ticket ticket) {
        if ("SUPERADMIN".equals(admin.getRole())) return true;
        return ticket.getOrder().getAsset().getCreator().getId().equals(admin.getId());
    }

    private void logAction(String discordId, String action, String details) {
        auditLogRepository.save(AuditLog.builder().adminDiscordId(discordId).action(action).details(details).build());
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> getTickets(@AuthenticationPrincipal OAuth2User principal) {
        User admin = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        var tickets = "SUPERADMIN".equals(admin.getRole())
                ? ticketRepository.findAllByOrderByCreatedAtDesc()
                : ticketRepository.findByOrderAssetCreatorIdOrderByCreatedAtDesc(admin.getId());

        var mapped = tickets.stream().map(this::mapTicketToDto).collect(Collectors.toList());
        return ResponseEntity.ok(mapped);
    }

    @GetMapping("/{ticketId}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getTicketDetails(@PathVariable UUID ticketId, @AuthenticationPrincipal OAuth2User principal) {
        User admin = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();

        if (!hasAccess(admin, ticket)) return ResponseEntity.status(403).build();

        var messages = ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(this::mapMessageToDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("ticket", mapTicketToDto(ticket), "messages", messages));
    }

    @PostMapping("/{ticketId}/messages")
    @Transactional
    public ResponseEntity<?> sendMessage(@PathVariable UUID ticketId, @RequestBody Map<String, String> payload, @AuthenticationPrincipal OAuth2User principal) {
        User admin = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();

        if (!hasAccess(admin, ticket)) return ResponseEntity.status(403).build();
        if ("CLOSED".equals(ticket.getStatus())) return ResponseEntity.badRequest().body(Map.of("error", "Ticket is closed"));

        TicketMessage msg = TicketMessage.builder().ticket(ticket).sender(admin).content(payload.get("content")).build();
        TicketMessage saved = ticketMessageRepository.save(msg);
        return ResponseEntity.ok(mapMessageToDto(saved));
    }

    @PostMapping("/{ticketId}/approve")
    @Transactional
    public ResponseEntity<?> approveTicket(@PathVariable UUID ticketId, @AuthenticationPrincipal OAuth2User principal) {
        User admin = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();

        if (!hasAccess(admin, ticket)) return ResponseEntity.status(403).build();

        ticket.setStatus("CLOSED");
        Order order = ticket.getOrder();
        order.setStatus("COMPLETED");

        ticketRepository.save(ticket);
        orderRepository.save(order);

        logAction(admin.getDiscordId(), "TICKET_APPROVE", "Approved ticket " + ticketId + " for order " + order.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{ticketId}/reject")
    @Transactional
    public ResponseEntity<?> rejectTicket(@PathVariable UUID ticketId, @AuthenticationPrincipal OAuth2User principal) {
        User admin = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();

        if (!hasAccess(admin, ticket)) return ResponseEntity.status(403).build();

        ticket.setStatus("CLOSED");
        Order order = ticket.getOrder();
        order.setStatus("CANCELLED");

        User buyer = order.getUser();
        int refundAmount = order.getAsset().getPrice();
        buyer.setBalance(buyer.getBalance() + refundAmount);
        userRepository.save(buyer);

        transactionRepository.save(Transaction.builder().user(buyer).amount(refundAmount).type("REFUND").build());

        ticketRepository.save(ticket);
        orderRepository.save(order);

        logAction(admin.getDiscordId(), "TICKET_REJECT", "Rejected ticket " + ticketId + " and refunded " + refundAmount + " R$ to user " + buyer.getDiscordId());
        return ResponseEntity.ok().build();
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