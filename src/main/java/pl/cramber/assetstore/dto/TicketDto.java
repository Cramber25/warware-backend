package pl.cramber.assetstore.dto;

import lombok.Builder;
import lombok.Data;
import pl.cramber.assetstore.entity.Ticket;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
public class TicketDto {
    private UUID id;
    private String status;
    private ZonedDateTime createdAt;
    private OrderDto order;

    @Data
    @Builder
    public static class OrderDto {
        private UUID id;
        private AssetDto asset;
        private UserDto user;
    }

    @Data
    @Builder
    public static class AssetDto {
        private String title;
        private Integer price;
        private String thumbnailUrl;
    }

    @Data
    @Builder
    public static class UserDto {
        private UUID id;
        private String discordUsername;
        private String discordId;
        private String robloxUsername;
        private String robloxId;
    }

    public static TicketDto fromEntity(Ticket t) {
        return TicketDto.builder()
                .id(t.getId())
                .status(t.getStatus())
                .createdAt(t.getCreatedAt() != null ? t.getCreatedAt() : ZonedDateTime.now())
                .order(OrderDto.builder()
                        .id(t.getOrder().getId())
                        .asset(AssetDto.builder()
                                .title(t.getOrder().getAsset().getTitle())
                                .price(t.getOrder().getAsset().getPrice())
                                .thumbnailUrl(t.getOrder().getAsset().getThumbnailUrl() != null ? t.getOrder().getAsset().getThumbnailUrl() : "")
                                .build())
                        .user(UserDto.builder()
                                .id(t.getOrder().getUser().getId())
                                .discordUsername(t.getOrder().getUser().getDiscordUsername() != null ? t.getOrder().getUser().getDiscordUsername() : t.getOrder().getUser().getDiscordId())
                                .discordId(t.getOrder().getUser().getDiscordId())
                                .robloxUsername(t.getOrder().getUser().getRobloxUsername() != null ? t.getOrder().getUser().getRobloxUsername() : "")
                                .robloxId(t.getOrder().getUser().getRobloxId() != null ? t.getOrder().getUser().getRobloxId() : "")
                                .build())
                        .build())
                .build();
    }
}