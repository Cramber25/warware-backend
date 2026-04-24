package pl.cramber.assetstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.cramber.assetstore.entity.*;
import pl.cramber.assetstore.repository.*;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository messageRepository;
    private final OrderRepository orderRepository;
    private final R2StorageService r2StorageService;

    public void addMessage(UUID ticketId, User sender, String content) {
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        TicketMessage message = TicketMessage.builder()
                .ticket(ticket)
                .sender(sender)
                .content(content)
                .build();
        messageRepository.save(message);
    }

    public List<TicketMessage> getChatHistory(UUID ticketId) {
        return messageRepository.findAllByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    @Transactional
    public String approveTicket(UUID ticketId, User admin) {
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
        Asset asset = ticket.getOrder().getAsset();

        if ("ADMIN".equals(admin.getRole()) && !asset.getCreator().getId().equals(admin.getId())) {
            throw new RuntimeException("UNAUTHORIZED_APPROVAL");
        }

        Order order = ticket.getOrder();
        order.setStatus("COMPLETED");
        orderRepository.save(order);

        ticket.setStatus("CLOSED_APPROVED");
        ticketRepository.save(ticket);

        return r2StorageService.generatePresignedUrl(asset.getR2FileKey(), 30);
    }
}