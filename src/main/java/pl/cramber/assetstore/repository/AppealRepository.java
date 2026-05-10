package pl.cramber.assetstore.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.cramber.assetstore.entity.Appeal;

import java.util.UUID;

@Repository
public interface AppealRepository extends JpaRepository<Appeal, UUID> {
    Page<Appeal> findByUserId(UUID userId, Pageable pageable);
    Page<Appeal> findByStatus(String status, Pageable pageable);
    boolean existsByUserIdAndStatus(UUID userId, String status);
}