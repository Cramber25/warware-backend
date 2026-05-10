package pl.cramber.assetstore.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "temp_bans", indexes = {
        @Index(name = "idx_temp_bans_unban_at", columnList = "unban_at"),
        @Index(name = "idx_temp_bans_discord_id", columnList = "discord_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TempBan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "discord_id", nullable = false)
    private String discordId;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "unban_at")
    private ZonedDateTime unbanAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}