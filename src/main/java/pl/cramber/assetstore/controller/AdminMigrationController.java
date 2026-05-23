package pl.cramber.assetstore.controller;

import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.webp.WebpWriter;
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
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

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
                String extension = getExtension(oldKey).toLowerCase();
                if (!extension.equals("webp") && !extension.isEmpty()) {
                    try {
                        String newKey = oldKey.substring(0, oldKey.lastIndexOf(".")) + ".webp";
                        convertAndReplaceInR2(oldKey, newKey);
                        asset.setThumbnailUrl(publicUrl + "/" + newKey);
                        migratedThumbnails++;
                        updated = true;
                        log.info("Converted thumbnail to WebP for asset {}", asset.getId());
                    } catch (Exception e) {
                        log.error("Failed to convert thumbnail for asset {}", asset.getId(), e);
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
                        String extension = getExtension(oldKey).toLowerCase();
                        if (!extension.equals("webp") && !extension.isEmpty()) {
                            try {
                                String newKey = oldKey.substring(0, oldKey.lastIndexOf(".")) + ".webp";
                                convertAndReplaceInR2(oldKey, newKey);
                                newGalleryUrls.add(publicUrl + "/" + newKey);
                                migratedGalleries++;
                                updated = true;
                                log.info("Converted gallery image to WebP for asset {}", asset.getId());
                            } catch (Exception e) {
                                log.error("Failed to convert gallery image {} for asset {}", oldKey, asset.getId(), e);
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

    private void convertAndReplaceInR2(String oldKey, String newKey) throws Exception {
        GetObjectRequest getReq = GetObjectRequest.builder()
                .bucket(publicBucketName)
                .key(oldKey)
                .build();
        ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getReq);

        ImmutableImage image = ImmutableImage.loader().fromBytes(objectBytes.asByteArray());

        if (image.width > 1920 || image.height > 1920) {
            image = image.max(1920, 1920);
        }

        byte[] webpBytes = image.bytes(WebpWriter.DEFAULT.withQ(80));

        PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(publicBucketName)
                .key(newKey)
                .contentType("image/webp")
                .build();
        s3Client.putObject(putReq, RequestBody.fromBytes(webpBytes));

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
        if (!key.contains(".")) return "";
        return key.substring(key.lastIndexOf(".") + 1);
    }
}