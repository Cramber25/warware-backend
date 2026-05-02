package pl.cramber.assetstore.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String discordId,
        String discordUsername,
        String discordAvatarUrl,
        String robloxId,
        String robloxUsername,
        String email,
        String role,
        Integer balance,
        boolean banned,
        ZonedDateTime createdAt,
        List<LoginLogDto> recentLogins
) {
    public record LoginLogDto(String ipAddress, ZonedDateTime createdAt) {}
}