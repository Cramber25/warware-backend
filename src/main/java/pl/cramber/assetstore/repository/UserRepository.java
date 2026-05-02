package pl.cramber.assetstore.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.cramber.assetstore.entity.User;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByRobloxId(String robloxId);
    Optional<User> findByDiscordId(String discordId);

    @Query("SELECT u FROM User u WHERE LOWER(u.discordUsername) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(u.robloxUsername) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(u.discordId) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<User> searchUsers(@Param("search") String search, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.robloxId = :robloxId")
    Optional<User> findByRobloxIdForUpdate(@Param("robloxId") String robloxId);
}