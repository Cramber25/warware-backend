package pl.cramber.assetstore.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
            "LEFT JOIN a.collections c " +
            "WHERE a.visibility = 'PUBLIC' " +
            "AND LOWER(a.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "AND (:categoryId IS NULL OR a.category.id = :categoryId) " +
            "AND (:tagIds IS NULL OR t.id IN :tagIds) " +
            "AND (:collectionIds IS NULL OR c.id IN :collectionIds)")
    Page<Asset> findPublicAssetsWithFilters(
            @Param("search") String search,
            @Param("categoryId") UUID categoryId,
            @Param("tagIds") List<UUID> tagIds,
            @Param("collectionIds") List<UUID> collectionIds,
            Pageable pageable);

    @Query("SELECT a FROM Asset a WHERE LOWER(a.title) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Asset> searchAllAdmin(@Param("search") String search, Pageable pageable);

    @Query("SELECT a FROM Asset a WHERE a.creator.id = :creatorId AND LOWER(a.title) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Asset> searchByCreatorIdAdmin(@Param("creatorId") UUID creatorId, @Param("search") String search, Pageable pageable);

    @Modifying
    @Query("UPDATE Asset a SET a.viewCount = a.viewCount + 1 WHERE a.id = :id")
    void incrementViewCount(@Param("id") UUID id);
}