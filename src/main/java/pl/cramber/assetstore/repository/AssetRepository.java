package pl.cramber.assetstore.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.cramber.assetstore.entity.Asset;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID> {
    List<Asset> findAllByCreatorId(UUID creatorId);
    List<Asset> findAllByVisibility(String visibility);
}