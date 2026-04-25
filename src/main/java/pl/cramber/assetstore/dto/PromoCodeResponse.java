package pl.cramber.assetstore.dto;

import java.util.UUID;

public record PromoCodeResponse(
        UUID id,
        String code,
        Integer discountPercent,
        Integer discountAmount,
        Integer usageLimit,
        long usageCount,
        boolean isPerUser,
        boolean isActive,
        CreatorDto creator,
        TargetAssetDto targetAsset,
        TargetCategoryDto targetCategory,
        TargetTagDto targetTag
) {
    public record CreatorDto(UUID id, String discordUsername) {}
    public record TargetAssetDto(UUID id, String title) {}
    public record TargetCategoryDto(UUID id, String name) {}
    public record TargetTagDto(UUID id, String name) {}
}