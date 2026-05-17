package pl.cramber.assetstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.cramber.assetstore.entity.*;
import pl.cramber.assetstore.repository.*;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository messageRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final DiscordNotificationService discordNotificationService;
    private final PayPalService payPalService;

    @Transactional
    public TicketMessage sendMessage(Ticket ticket, User sender, String content) {
        TicketMessage message = TicketMessage.builder()
                .ticket(ticket)
                .sender(sender)
                .content(content)
                .build();
        return messageRepository.save(message);
    }

    @Transactional
    public Order approveTicket(Ticket ticket) {
        ticket.setStatus("CLOSED");
        Order order = ticket.getOrder();
        order.setStatus("COMPLETED");
        ticketRepository.save(ticket);
        orderRepository.save(order);
        discordNotificationService.sendOrderCompleteDM(order.getUser().getDiscordId(), order.getAsset().getTitle());
        return order;
    }

    @Transactional
    public Order rejectTicket(Ticket ticket) {
        ticket.setStatus("CLOSED");
        Order order = ticket.getOrder();
        order.setStatus("CANCELLED");

        if ("ROBUX".equals(order.getPaymentMethod())) {
            User buyer = order.getUser();
            int refundAmount = order.getPurchasePrice();
            buyer.setBalance(buyer.getBalance() + refundAmount);
            userRepository.save(buyer);

            transactionRepository.save(Transaction.builder()
                    .user(buyer)
                    .amount(refundAmount)
                    .type("REFUND")
                    .currency("ROBUX")
                    .build());
        } else if ("PAYPAL".equals(order.getPaymentMethod()) && order.getPaypalCaptureId() != null) {
            payPalService.refundCapture(order.getAsset().getPaypalEnvKey(), order.getPaypalCaptureId());

            transactionRepository.save(Transaction.builder()
                    .user(order.getUser())
                    .amount(0)
                    .amountUsd(order.getPurchasePriceUsd())
                    .type("REFUND_PAYPAL")
                    .currency("USD")
                    .build());
        }

        ticketRepository.save(ticket);
        return orderRepository.save(order);
    }
}