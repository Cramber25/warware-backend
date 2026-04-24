package pl.cramber.assetstore.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.cramber.assetstore.entity.PromoCodeUsage;

import java.util.UUID;

@Repository
public interface PromoCodeUsageRepository extends JpaRepository<PromoCodeUsage, UUID> {
    boolean existsByUserIdAndPromoCodeId(UUID userId, UUID promoCodeId);
    long countByPromoCodeId(UUID promoCodeId);
}