package pl.cramber.assetstore.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.cramber.assetstore.entity.TempBan;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TempBanRepository extends JpaRepository<TempBan, UUID> {
    List<TempBan> findAllByUnbanAtBefore(ZonedDateTime time);
    void deleteByDiscordIdAndGuildId(String discordId, String guildId);
    void deleteByDiscordId(String discordId);
}