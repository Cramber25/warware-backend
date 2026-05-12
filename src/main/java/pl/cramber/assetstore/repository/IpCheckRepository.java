package pl.cramber.assetstore.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.cramber.assetstore.entity.IpCheck;

import java.time.LocalDateTime;

@Repository
public interface IpCheckRepository extends JpaRepository<IpCheck, String> {

    @Modifying
    @Query("DELETE FROM IpCheck i WHERE i.createdAt < :date")
    void deleteOlderThan(@Param("date") LocalDateTime date);
}