package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.cramber.assetstore.entity.Asset;
import pl.cramber.assetstore.repository.AssetRepository;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/system")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class AdminMigrationController {

    private final AssetRepository assetRepository;
    private final S3Client s3Client;

    @Value("${cloud.r2.public-bucket-name}")
    private String publicBucketName;

    @Value("${cloud.r2.public-url}")
    private String publicUrl;

    @PostMapping("/migrate-images")
    @Transactional
    public ResponseEntity<?> migrateImages(@AuthenticationPrincipal OAuth2User principal) {
        String authorities = principal.getAuthorities().toString();
        if (!authorities.contains("ROLE_SUPERADMIN")) {
            return ResponseEntity.status(403).build();
        }

        List<Asset> assets = assetRepository.findAll();
        int migratedThumbnails = 0;
        int migratedGalleries = 0;

        for (Asset asset : assets) {
            boolean updated = false;

            if (asset.getThumbnailUrl() != null && asset.getThumbnailUrl().contains(publicUrl)) {
                String oldKey = extractKey(asset.getThumbnailUrl());
                if (!oldKey.startsWith("assets/" + asset.getId() + "/thumbnail")) {
                    try {
                        String extension = getExtension(oldKey);
                        String newKey = "assets/" + asset.getId() + "/thumbnail." + extension;

                        moveObjectInR2(oldKey, newKey);
                        asset.setThumbnailUrl(publicUrl + "/" + newKey);
                        migratedThumbnails++;
                        updated = true;
                        log.info("Migrated thumbnail for asset {}", asset.getId());
                    } catch (Exception e) {
                        log.error("Failed to migrate thumbnail for asset {}", asset.getId(), e);
                    }
                }
            }

            if (asset.getGalleryUrls() != null && !asset.getGalleryUrls().trim().isEmpty()) {
                String[] oldUrls = asset.getGalleryUrls().split("\n");
                List<String> newGalleryUrls = new ArrayList<>();

                for (String oldUrl : oldUrls) {
                    oldUrl = oldUrl.trim();
                    if (oldUrl.isEmpty()) continue;

                    if (oldUrl.contains(publicUrl)) {
                        String oldKey = extractKey(oldUrl);
                        if (!oldKey.startsWith("assets/" + asset.getId() + "/gallery/")) {
                            try {
                                String originalFilename = oldKey.contains("-")
                                        ? oldKey.substring(oldKey.lastIndexOf("-") + 1)
                                        : oldKey.substring(oldKey.lastIndexOf("/") + 1);

                                String newKey = "assets/" + asset.getId() + "/gallery/" + originalFilename;

                                moveObjectInR2(oldKey, newKey);
                                newGalleryUrls.add(publicUrl + "/" + newKey);
                                migratedGalleries++;
                                updated = true;
                                log.info("Migrated gallery image for asset {}", asset.getId());
                            } catch (Exception e) {
                                log.error("Failed to migrate gallery image {} for asset {}", oldKey, asset.getId(), e);
                                newGalleryUrls.add(oldUrl);
                            }
                        } else {
                            newGalleryUrls.add(oldUrl);
                        }
                    } else {
                        newGalleryUrls.add(oldUrl);
                    }
                }

                if (updated) {
                    asset.setGalleryUrls(String.join("\n", newGalleryUrls));
                }
            }

            if (updated) {
                assetRepository.save(asset);
            }
        }

        return ResponseEntity.ok(Map.of(
                "status", "Migration Complete",
                "thumbnailsMigrated", migratedThumbnails,
                "galleryImagesMigrated", migratedGalleries
        ));
    }

    private void moveObjectInR2(String oldKey, String newKey) {
        String encodedSourceKey = URLEncoder.encode(publicBucketName + "/" + oldKey, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%2F", "/");

        CopyObjectRequest copyReq = CopyObjectRequest.builder()
                .copySource(encodedSourceKey)
                .destinationBucket(publicBucketName)
                .destinationKey(newKey)
                .build();
        s3Client.copyObject(copyReq);

        DeleteObjectRequest deleteReq = DeleteObjectRequest.builder()
                .bucket(publicBucketName)
                .key(oldKey)
                .build();
        s3Client.deleteObject(deleteReq);
    }

    private String extractKey(String url) {
        return url.replace(publicUrl + "/", "");
    }

    private String getExtension(String key) {
        if (!key.contains(".")) return "png";
        return key.substring(key.lastIndexOf(".") + 1);
    }
}