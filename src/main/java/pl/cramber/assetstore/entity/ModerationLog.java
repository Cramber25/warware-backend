package pl.cramber.assetstore.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "moderation_logs", indexes = {
        @Index(name = "idx_modlog_target", columnList = "target_discord_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModerationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "target_discord_id", nullable = false)
    private String targetDiscordId;

    @Column(name = "target_discord_username")
    private String targetDiscordUsername;

    @Column(name = "moderator_discord_id", nullable = false)
    private String moderatorDiscordId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "duration")
    private String duration;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}