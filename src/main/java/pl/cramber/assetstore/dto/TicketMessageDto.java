package pl.cramber.assetstore.dto;

import lombok.Builder;
import lombok.Data;
import pl.cramber.assetstore.entity.TicketMessage;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
public class TicketMessageDto {
    private UUID id;
    private String content;
    private ZonedDateTime createdAt;
    private SenderDto sender;

    @Data
    @Builder
    public static class SenderDto {
        private UUID id;
        private String discordUsername;
    }

    public static TicketMessageDto fromEntity(TicketMessage m) {
        return TicketMessageDto.builder()
                .id(m.getId())
                .content(m.getContent() != null ? m.getContent() : "")
                .createdAt(m.getCreatedAt() != null ? m.getCreatedAt() : ZonedDateTime.now())
                .sender(SenderDto.builder()
                        .id(m.getSender().getId())
                        .discordUsername(m.getSender().getDiscordUsername() != null ? m.getSender().getDiscordUsername() : m.getSender().getDiscordId())
                        .build())
                .build();
    }
}