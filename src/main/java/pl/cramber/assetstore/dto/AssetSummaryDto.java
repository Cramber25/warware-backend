package pl.cramber.assetstore.dto;

import pl.cramber.assetstore.entity.Asset;
import java.time.ZonedDateTime;
import java.util.UUID;

public class AssetSummaryDto {
    public final UUID id;
    public final String title;
    public final String description;
    public final Integer price;
    public final Double priceUsd;
    public final Boolean paypalEnabled;
    public final String paypalClientId;
    public final Object category;
    public final Object tags;
    public final String thumbnailUrl;
    public final String galleryUrls;
    public final Integer viewCount;
    public final Double averageRating;
    public final Integer ratingCount;
    public final String deliveryType;
    public final String visibility;
    public final ZonedDateTime createdAt;

    public AssetSummaryDto(Asset asset, String paypalClientId) {
        this.id = asset.getId();
        this.title = asset.getTitle();
        this.description = asset.getDescription();
        this.price = asset.getPrice();
        this.priceUsd = asset.getPriceUsd();
        this.paypalEnabled = asset.getPaypalEnabled();
        this.paypalClientId = paypalClientId;
        this.category = asset.getCategory();
        this.tags = asset.getTags();
        this.thumbnailUrl = asset.getThumbnailUrl();
        this.galleryUrls = asset.getGalleryUrls();
        this.viewCount = asset.getViewCount();
        this.averageRating = Math.round(asset.getAverageRating() * 10.0) / 10.0;
        this.ratingCount = asset.getRatingCount();
        this.deliveryType = asset.getDeliveryType();
        this.visibility = asset.getVisibility();
        this.createdAt = asset.getCreatedAt();
    }
}