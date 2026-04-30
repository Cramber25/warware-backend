package pl.cramber.assetstore.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.cramber.assetstore.entity.Asset;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID> {

    List<Asset> findAllByCreatorId(UUID creatorId);

    List<Asset> findAllByVisibility(String visibility);

    @Query("SELECT DISTINCT a FROM Asset a " +
            "LEFT JOIN a.tags t " +
            "WHERE a.visibility = 'PUBLIC' " +
            "AND LOWER(a.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "AND (:categoryId IS NULL OR a.category.id = :categoryId) " +
            "AND (:tagIds IS NULL OR t.id IN :tagIds)")
    Page<Asset> findPublicAssetsWithFilters(
            @Param("search") String search,
            @Param("categoryId") UUID categoryId,
            @Param("tagIds") List<UUID> tagIds,
            Pageable pageable);
}