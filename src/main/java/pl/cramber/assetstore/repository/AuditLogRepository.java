package pl.cramber.assetstore.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.cramber.assetstore.entity.AuditLog;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("SELECT a FROM AuditLog a WHERE LOWER(a.action) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(a.adminDiscordId) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(a.details) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<AuditLog> searchLogs(@Param("search") String search, Pageable pageable);
}