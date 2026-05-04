package pl.cramber.assetstore.dto;

import lombok.Data;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class AssetRequest {
    private String title;
    private String description;
    private Integer price;
    private String visibility;
    private String deliveryType;
    private String r2FileKey;
    private String thumbnailUrl;
    private String galleryUrls;
    private UUID categoryId;
    private List<UUID> tagIds;
    private ZonedDateTime createdAt;
    private List<UUID> collectionIds;
}