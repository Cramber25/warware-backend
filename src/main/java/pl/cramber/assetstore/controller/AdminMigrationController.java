package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.cramber.assetstore.entity.Asset;
import pl.cramber.assetstore.repository.AssetRepository;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Value("${cloud.r2.bucket-name}")
    private String privateBucketName;

    @GetMapping("/list-r2-files")
    public ResponseEntity<?> listAllR2Files(@AuthenticationPrincipal OAuth2User principal) {
        String authorities = principal.getAuthorities().toString();
        if (!authorities.contains("ROLE_SUPERADMIN")) {
            return ResponseEntity.status(403).build();
        }

        ListObjectsV2Request listReq = ListObjectsV2Request.builder()
                .bucket(publicBucketName)
                .build();

        List<String> fileKeys = s3Client.listObjectsV2Paginator(listReq)
                .contents()
                .stream()
                .map(S3Object::key)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "totalFiles", fileKeys.size(),
                "files", fileKeys
        ));
    }

    @PostMapping("/migrate-private-assets")
    @Transactional
    public ResponseEntity<?> migratePrivateAssets(@AuthenticationPrincipal OAuth2User principal) {
        String authorities = principal.getAuthorities().toString();
        if (!authorities.contains("ROLE_SUPERADMIN")) {
            return ResponseEntity.status(403).build();
        }

        List<Asset> assets = assetRepository.findAll();
        int migratedFiles = 0;

        for (Asset asset : assets) {
            String oldKey = asset.getR2FileKey();
            if (oldKey == null || oldKey.isEmpty()) continue;

            String extension = oldKey.contains(".") ? oldKey.substring(oldKey.lastIndexOf(".")) : "";
            String expectedKey = "assets/" + asset.getId() + extension;

            if (!oldKey.equals(expectedKey)) {
                try {
                    String encodedSourceKey = URLEncoder.encode(privateBucketName + "/" + oldKey, StandardCharsets.UTF_8)
                            .replace("+", "%20")
                            .replace("%2F", "/");

                    CopyObjectRequest copyReq = CopyObjectRequest.builder()
                            .copySource(encodedSourceKey)
                            .destinationBucket(privateBucketName)
                            .destinationKey(expectedKey)
                            .build();
                    s3Client.copyObject(copyReq);

                    DeleteObjectRequest deleteReq = DeleteObjectRequest.builder()
                            .bucket(privateBucketName)
                            .key(oldKey)
                            .build();
                    s3Client.deleteObject(deleteReq);

                    asset.setR2FileKey(expectedKey);
                    assetRepository.save(asset);
                    migratedFiles++;
                    log.info("Migrated private file for asset {}", asset.getId());
                } catch (Exception e) {
                    log.error("Failed to migrate private file for asset {}", asset.getId(), e);
                }
            }
        }

        return ResponseEntity.ok(Map.of(
                "status", "Private assets migration complete",
                "migratedFiles", migratedFiles
        ));
    }
}