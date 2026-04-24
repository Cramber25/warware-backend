package pl.cramber.assetstore.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.cramber.assetstore.entity.Ticket;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    List<Ticket> findByOrderUserIdOrderByCreatedAtDesc(UUID userId);
    List<Ticket> findByOrderAssetCreatorIdOrderByCreatedAtDesc(UUID creatorId);
    List<Ticket> findAllByOrderByCreatedAtDesc();
}