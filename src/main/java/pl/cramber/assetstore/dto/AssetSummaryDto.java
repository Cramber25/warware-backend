package pl.cramber.assetstore.dto;

import pl.cramber.assetstore.entity.Asset;
import java.util.UUID;

public class AssetSummaryDto {
    public final UUID id;
    public final String title;
    public final Object price;
    public final Object category;
    public final Object tags;
    public final String thumbnailUrl;
    public final Integer viewCount;
    public final Double averageRating;
    public final Integer ratingCount;

    public AssetSummaryDto(Asset asset) {
        this.id = asset.getId();
        this.title = asset.getTitle();
        this.price = asset.getPrice();
        this.category = asset.getCategory();
        this.tags = asset.getTags();
        this.thumbnailUrl = asset.getThumbnailUrl();
        this.viewCount = asset.getViewCount();
        this.averageRating = Math.round(asset.getAverageRating() * 10.0) / 10.0;
        this.ratingCount = asset.getRatingCount();
    }
}