package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pl.cramber.assetstore.dto.AssetRequest;
import pl.cramber.assetstore.entity.Asset;
import pl.cramber.assetstore.entity.AuditLog;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.*;
import pl.cramber.assetstore.service.R2Service;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/assets")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class AdminAssetController {

    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final CollectionRepository collectionRepository;
    private final AuditLogRepository auditLogRepository;
    private final R2Service r2Service;

    private void logAction(OAuth2User principal, String action, String details) {
        if (principal == null) return;
        auditLogRepository.save(AuditLog.builder()
                .adminDiscordId(principal.getAttribute("id"))
                .action(action)
                .details(details)
                .build());
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Page<Asset>> getAllAssets(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @AuthenticationPrincipal OAuth2User principal) {

        User admin = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        String safeSearch = search == null ? "" : search;
        Sort.Direction direction = sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Page<Asset> assets = "SUPERADMIN".equals(admin.getRole())
                ? assetRepository.searchAllAdmin(safeSearch, pageable)
                : assetRepository.searchByCreatorIdAdmin(admin.getId(), safeSearch, pageable);

        assets.forEach(asset -> {
            if (asset.getTags() != null) {
                asset.getTags().size();
            }
            if (asset.getCollections() != null) {
                asset.getCollections().size();
            }
        });

        return ResponseEntity.ok(assets);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<Asset> getAssetById(@PathVariable UUID id) {
        return assetRepository.findById(id)
                .map(asset -> {
                    if (asset.getTags() != null) {
                        asset.getTags().size();
                    }
                    if (asset.getCollections() != null) {
                        asset.getCollections().size();
                    }
                    return ResponseEntity.ok(asset);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Asset> createAsset(
            @RequestBody AssetRequest request,
            @AuthenticationPrincipal OAuth2User principal) {

        User creator = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        String role = principal.getAuthorities().toString();

        if (!role.contains("ROLE_SUPERADMIN") && !request.getR2FileKey().startsWith("assets/" + creator.getId() + "/")) {
            return ResponseEntity.status(403).build();
        }

        Asset asset = Asset.builder()
                .creator(creator)
                .title(request.getTitle())
                .description(request.getDescription())
                .price(request.getPrice())
                .visibility(request.getVisibility())
                .deliveryType(request.getDeliveryType())
                .r2FileKey(request.getR2FileKey())
                .thumbnailUrl(request.getThumbnailUrl())
                .galleryUrls(request.getGalleryUrls())
                .createdAt(request.getCreatedAt() != null ? request.getCreatedAt() : ZonedDateTime.now())
                .build();

        applyRelations(asset, request);
        Asset savedAsset = assetRepository.save(asset);

        logAction(principal, "CREATE_ASSET", "Created asset: " + savedAsset.getTitle());
        return ResponseEntity.ok(savedAsset);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Asset> updateAsset(
            @PathVariable UUID id,
            @RequestBody AssetRequest request,
            @AuthenticationPrincipal OAuth2User principal) {

        Asset asset = assetRepository.findById(id).orElseThrow();
        User admin = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        String role = principal.getAuthorities().toString();

        if (!role.contains("ROLE_SUPERADMIN") && !asset.getCreator().getId().equals(admin.getId())) {
            return ResponseEntity.status(403).build();
        }

        asset.setTitle(request.getTitle());
        asset.setDescription(request.getDescription());
        asset.setPrice(request.getPrice());
        asset.setVisibility(request.getVisibility());
        asset.setDeliveryType(request.getDeliveryType());
        asset.setThumbnailUrl(request.getThumbnailUrl());
        asset.setGalleryUrls(request.getGalleryUrls());

        if (role.contains("ROLE_SUPERADMIN") && request.getCreatedAt() != null) {
            asset.setCreatedAt(request.getCreatedAt());
        }

        if (request.getR2FileKey() != null && !request.getR2FileKey().isEmpty() && !request.getR2FileKey().equals(asset.getR2FileKey())) {
            if (!role.contains("ROLE_SUPERADMIN") && !request.getR2FileKey().startsWith("assets/" + admin.getId() + "/")) {
                return ResponseEntity.status(403).build();
            }
            String oldFileKey = asset.getR2FileKey();
            asset.setR2FileKey(request.getR2FileKey());
            try { r2Service.deleteFile(oldFileKey); } catch (Exception ignored) {}
        }

        applyRelations(asset, request);
        Asset savedAsset = assetRepository.save(asset);

        logAction(principal, "UPDATE_ASSET", "Updated asset: " + savedAsset.getTitle());
        return ResponseEntity.ok(savedAsset);
    }

    private void applyRelations(Asset asset, AssetRequest request) {
        if (request.getCategoryId() != null) {
            asset.setCategory(categoryRepository.findById(request.getCategoryId()).orElse(null));
        } else {
            asset.setCategory(null);
        }

        if (request.getTagIds() != null) {
            asset.setTags(new HashSet<>(tagRepository.findAllById(request.getTagIds())));
        } else {
            asset.setTags(new HashSet<>());
        }

        if (request.getCollectionIds() != null) {
            asset.setCollections(new HashSet<>(collectionRepository.findAllById(request.getCollectionIds())));
        } else {
            asset.setCollections(new HashSet<>());
        }
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteAsset(
            @PathVariable UUID id,
            @AuthenticationPrincipal OAuth2User principal) {

        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ASSET_NOT_FOUND"));

        User admin = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        String role = principal.getAuthorities().toString();

        if (!role.contains("ROLE_SUPERADMIN") && !asset.getCreator().getId().equals(admin.getId())) {
            return ResponseEntity.status(403).build();
        }

        asset.setVisibility("ARCHIVED");
        assetRepository.save(asset);

        logAction(principal, "ARCHIVE_ASSET", "Archived (Soft Deleted) asset: " + asset.getTitle() + " (" + asset.getId() + ")");
        return ResponseEntity.ok().build();
    }
}