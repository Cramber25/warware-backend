package pl.cramber.assetstore.dto;

import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
public class PenaltyDto {
    private UUID id;
    private String type;
    private String reason;
    private ZonedDateTime expiresAt;
    private boolean canAppeal;
    private AppealDto appeal;
}