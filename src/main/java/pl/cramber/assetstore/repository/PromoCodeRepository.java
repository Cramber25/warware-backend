package pl.cramber.assetstore.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.cramber.assetstore.entity.PromoCode;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromoCodeRepository extends JpaRepository<PromoCode, UUID> {
    Optional<PromoCode> findByCodeAndIsActiveTrue(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PromoCode p WHERE p.code = :code AND p.isActive = true AND p.isArchived = false")
    Optional<PromoCode> findByCodeForUpdate(@Param("code") String code);

    @Query("SELECT p FROM PromoCode p WHERE p.isArchived = false AND LOWER(p.code) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<PromoCode> searchPromoCodes(@Param("search") String search, Pageable pageable);

    @Query("SELECT p FROM PromoCode p WHERE p.isArchived = false AND p.creator.id = :creatorId AND LOWER(p.code) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<PromoCode> searchCreatorPromoCodes(@Param("creatorId") UUID creatorId, @Param("search") String search, Pageable pageable);
}