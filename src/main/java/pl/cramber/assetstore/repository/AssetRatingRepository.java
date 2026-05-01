package pl.cramber.assetstore.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.cramber.assetstore.entity.AssetRating;

import java.util.UUID;

@Repository
public interface AssetRatingRepository extends JpaRepository<AssetRating, UUID> {
    Page<AssetRating> findByAssetId(UUID assetId, Pageable pageable);
    boolean existsByAssetIdAndUserId(UUID assetId, UUID userId);
}