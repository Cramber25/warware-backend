package pl.cramber.assetstore.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.cramber.assetstore.entity.Ticket;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    List<Ticket> findByOrderUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT t FROM Ticket t WHERE LOWER(t.order.asset.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(t.status) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(t.order.user.discordUsername) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Ticket> searchTickets(@Param("search") String search, Pageable pageable);

    @Query("SELECT t FROM Ticket t WHERE t.order.asset.creator.id = :creatorId AND (LOWER(t.order.asset.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(t.status) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(t.order.user.discordUsername) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Ticket> searchCreatorTickets(@Param("creatorId") UUID creatorId, @Param("search") String search, Pageable pageable);
}