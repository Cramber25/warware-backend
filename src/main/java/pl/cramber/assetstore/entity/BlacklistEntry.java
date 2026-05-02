package pl.cramber.assetstore.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "blacklist_entries", indexes = {
        @Index(name = "idx_blacklist_roblox_id", columnList = "roblox_id"),
        @Index(name = "idx_blacklist_discord_id", columnList = "discord_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlacklistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "discord_id")
    private String discordId;

    @Column(name = "roblox_id")
    private String robloxId;

    @Column(name = "roblox_username")
    private String robloxUsername;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "markdown_info", columnDefinition = "TEXT")
    private String markdownInfo;

    @Column(name = "admin_discord_id")
    private String adminDiscordId;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}