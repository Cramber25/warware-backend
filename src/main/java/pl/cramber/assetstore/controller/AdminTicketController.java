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
import pl.cramber.assetstore.dto.TicketDto;
import pl.cramber.assetstore.dto.TicketMessageDto;
import pl.cramber.assetstore.entity.*;
import pl.cramber.assetstore.repository.*;
import pl.cramber.assetstore.service.TicketService;

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
    private final AuditLogRepository auditLogRepository;
    private final TicketService ticketService;

    private boolean hasAccess(User admin, Ticket ticket) {
        if ("SUPERADMIN".equals(admin.getRole())) return true;
        return ticket.getOrder().getAsset().getCreator().getId().equals(admin.getId());
    }

    private void logAction(String discordId, String action, String details) {
        auditLogRepository.save(AuditLog.builder().adminDiscordId(discordId).action(action).details(details).build());
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Page<TicketDto>> getTickets(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @AuthenticationPrincipal OAuth2User principal) {

        User admin = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();

        String safeSearch = search == null ? "" : search;
        Sort.Direction direction = sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Page<Ticket> tickets = "SUPERADMIN".equals(admin.getRole())
                ? ticketRepository.searchTickets(safeSearch, pageable)
                : ticketRepository.searchCreatorTickets(admin.getId(), safeSearch, pageable);

        return ResponseEntity.ok(tickets.map(TicketDto::fromEntity));
    }

    @GetMapping("/{ticketId}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getTicketDetails(@PathVariable UUID ticketId, @AuthenticationPrincipal OAuth2User principal) {
        User admin = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();

        if (!hasAccess(admin, ticket)) return ResponseEntity.status(403).build();

        var messages = ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(TicketMessageDto::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("ticket", TicketDto.fromEntity(ticket), "messages", messages));
    }

    @PostMapping("/{ticketId}/messages")
    @Transactional
    public ResponseEntity<?> sendMessage(@PathVariable UUID ticketId, @RequestBody Map<String, String> payload, @AuthenticationPrincipal OAuth2User principal) {
        User admin = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();

        if (!hasAccess(admin, ticket)) return ResponseEntity.status(403).build();

        if ("CLOSED".equals(ticket.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ticket is closed"));
        }

        TicketMessage saved = ticketService.sendMessage(ticket, admin, payload.get("content"));
        return ResponseEntity.ok(TicketMessageDto.fromEntity(saved));
    }

    @PostMapping("/{ticketId}/approve")
    @Transactional
    public ResponseEntity<?> approveTicket(@PathVariable UUID ticketId, @AuthenticationPrincipal OAuth2User principal) {
        User admin = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();

        if (!hasAccess(admin, ticket)) return ResponseEntity.status(403).build();

        Order completedOrder = ticketService.approveTicket(ticket);

        logAction(admin.getDiscordId(), "TICKET_APPROVE", "Approved ticket " + ticketId + " for order " + completedOrder.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{ticketId}/reject")
    @Transactional
    public ResponseEntity<?> rejectTicket(@PathVariable UUID ticketId, @AuthenticationPrincipal OAuth2User principal) {
        User admin = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();

        if (!hasAccess(admin, ticket)) return ResponseEntity.status(403).build();

        Order cancelledOrder = ticketService.rejectTicket(ticket);

        logAction(admin.getDiscordId(), "TICKET_REJECT", "Rejected ticket " + ticketId + " and refunded " + cancelledOrder.getPurchasePrice() + " R$ to user " + cancelledOrder.getUser().getDiscordId());
        return ResponseEntity.ok().build();
    }
}