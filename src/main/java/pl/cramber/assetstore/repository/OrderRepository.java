package pl.cramber.assetstore.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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

    Optional<Order> findByUserIdAndAssetIdAndStatusNot(UUID userId, UUID assetId, String status);
    List<Order> findAllByOrderTypeOrderByCreatedAtDesc(String orderType);
    List<Order> findByAssetCreatorIdAndOrderTypeOrderByCreatedAtDesc(UUID creatorId, String orderType);

    boolean existsByUserIdAndAssetIdAndStatus(UUID userId, UUID assetId, String status);
}