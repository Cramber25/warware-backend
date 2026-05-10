package pl.cramber.assetstore.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.cramber.assetstore.entity.BlacklistEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BlacklistEntryRepository extends JpaRepository<BlacklistEntry, UUID> {

    boolean existsByRobloxIdAndIsActiveTrue(String robloxId);

    List<BlacklistEntry> findAllByDiscordIdAndIsActiveTrue(String discordId);

    List<BlacklistEntry> findAllByRobloxIdAndIsActiveTrue(String robloxId);

    List<BlacklistEntry> findAllByDiscordIdOrderByCreatedAtDesc(String discordId);

    @Query("SELECT b FROM BlacklistEntry b WHERE LOWER(b.robloxUsername) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(b.discordId) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(b.reason) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<BlacklistEntry> searchBlacklists(@Param("search") String search, Pageable pageable);

    @Query("SELECT b FROM BlacklistEntry b WHERE b.isActive = true AND (LOWER(b.robloxUsername) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(b.discordId) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(b.reason) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<BlacklistEntry> searchActiveBlacklists(@Param("search") String search, Pageable pageable);
}