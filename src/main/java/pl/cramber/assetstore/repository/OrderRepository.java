package pl.cramber.assetstore.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.cramber.assetstore.entity.Order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByUserId(UUID userId);

    @EntityGraph(attributePaths = {"asset"})
    List<Order> findWithAssetByUserId(UUID userId);

    @EntityGraph(attributePaths = {"asset"})
    Page<Order> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, String status, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.orderType = :orderType AND (LOWER(o.asset.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(o.user.discordUsername) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(o.user.discordId) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Order> searchSales(@Param("orderType") String orderType, @Param("search") String search, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.asset.creator.id = :creatorId AND o.orderType = :orderType AND (LOWER(o.asset.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(o.user.discordUsername) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(o.user.discordId) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Order> searchCreatorSales(@Param("creatorId") UUID creatorId, @Param("orderType") String orderType, @Param("search") String search, Pageable pageable);

    Optional<Order> findByUserIdAndAssetIdAndStatusNot(UUID userId, UUID assetId, String status);

    boolean existsByUserIdAndAssetIdAndStatus(UUID userId, UUID assetId, String status);
}