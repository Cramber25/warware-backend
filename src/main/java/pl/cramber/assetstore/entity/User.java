package pl.cramber.assetstore.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "discord_id", nullable = false, unique = true)
    private String discordId;

    @Column(name = "discord_username")
    private String discordUsername;

    @Column(name = "discord_avatar_url", columnDefinition = "TEXT")
    private String discordAvatarUrl;

    @Column(name = "roblox_id", unique = true)
    private String robloxId;

    @Column(name = "roblox_username")
    private String robloxUsername;

    @Column(name = "roblox_avatar_url", columnDefinition = "TEXT")
    private String robloxAvatarUrl;

    @Column(nullable = false)
    @Builder.Default
    private String role = "USER";

    @Column(nullable = false)
    @Builder.Default
    private Integer balance = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean banned = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}