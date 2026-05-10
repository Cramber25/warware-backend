package pl.cramber.assetstore.dto;

import lombok.Builder;
import lombok.Data;
import pl.cramber.assetstore.entity.Appeal;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Builder
public class AppealDto {
    private UUID id;
    private String appealType;
    private UUID referenceId;
    private String content;
    private String status;
    private String adminReply;
    private ZonedDateTime createdAt;
    private ZonedDateTime resolvedAt;

    public static AppealDto fromEntity(Appeal appeal) {
        return AppealDto.builder()
                .id(appeal.getId())
                .appealType(appeal.getAppealType())
                .referenceId(appeal.getReferenceId())
                .content(appeal.getContent())
                .status(appeal.getStatus())
                .adminReply(appeal.getAdminReply())
                .createdAt(appeal.getCreatedAt())
                .resolvedAt(appeal.getResolvedAt())
                .build();
    }
}