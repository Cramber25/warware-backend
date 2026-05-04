package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pl.cramber.assetstore.dto.AssetSummaryDto;
import pl.cramber.assetstore.entity.Asset;
import pl.cramber.assetstore.repository.AssetRepository;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class AssetController {

    private final AssetRepository assetRepository;

    @GetMapping
    public Page<AssetSummaryDto> getPublicAssets(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) List<UUID> tagIds,
            @RequestParam(required = false) List<UUID> collectionIds,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        String validSearch = (search != null) ? search : "";
        List<UUID> validTagIds = (tagIds != null && !tagIds.isEmpty()) ? tagIds : null;
        List<UUID> validCollectionIds = (collectionIds != null && !collectionIds.isEmpty()) ? collectionIds : null;

        Sort.Direction direction = sortDirection.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;

        String validSortBy = switch (sortBy.toLowerCase()) {
            case "price" -> "price";
            case "title" -> "title";
            case "viewcount" -> "viewCount";
            case "rating" -> "averageRating";
            default -> "createdAt";
        };

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, validSortBy));

        return assetRepository.findPublicAssetsWithFilters(validSearch, categoryId, validTagIds, validCollectionIds, pageable)
                .map(AssetSummaryDto::new);
    }

    @GetMapping("/{id}")
    @Transactional
    public ResponseEntity<Asset> getAssetById(@PathVariable UUID id) {
        return assetRepository.findById(id)
                .filter(asset -> !asset.getVisibility().equals("PRIVATE") && !asset.getVisibility().equals("ARCHIVED"))
                .map(asset -> {
                    assetRepository.incrementViewCount(id);
                    asset.setViewCount(asset.getViewCount() + 1);

                    asset.setAverageRating(Math.round(asset.getAverageRating() * 10.0) / 10.0);
                    return ResponseEntity.ok(asset);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}