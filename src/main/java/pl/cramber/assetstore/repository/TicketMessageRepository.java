package pl.cramber.assetstore.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.cramber.assetstore.entity.TicketMessage;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketMessageRepository extends JpaRepository<TicketMessage, UUID> {
    List<TicketMessage> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
    List<TicketMessage> findAllByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}