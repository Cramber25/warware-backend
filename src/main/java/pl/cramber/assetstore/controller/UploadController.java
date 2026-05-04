package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.AssetRepository;
import pl.cramber.assetstore.repository.UserRepository;
import pl.cramber.assetstore.service.R2Service;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/uploads")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class UploadController {

    private final R2Service r2Service;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;

    @GetMapping("/generate-image-url")
    public ResponseEntity<?> getUploadUrl(
            @RequestParam String filename,
            @RequestParam String type,
            @RequestParam(required = false) UUID assetId,
            @RequestParam(required = false) String folder,
            @AuthenticationPrincipal OAuth2User principal) {

        User user = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();

        if (assetId != null && !hasAssetAccess(user, assetId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("FORBIDDEN_ASSET_ACCESS");
        }

        try {
            return ResponseEntity.ok(r2Service.generateImageUploadUrl(filename, type, assetId, folder));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/generate-image-urls")
    public ResponseEntity<?> getBulkUploadUrls(
            @RequestBody BulkUploadRequest request,
            @AuthenticationPrincipal OAuth2User principal) {

        User user = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();

        if (request.assetId() != null && !hasAssetAccess(user, request.assetId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("FORBIDDEN_ASSET_ACCESS");
        }

        try {
            return ResponseEntity.ok(r2Service.generateImageUploadUrls(request.filenames(), request.type(), request.assetId(), request.folder()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private boolean hasAssetAccess(User user, UUID assetId) {
        return assetRepository.findById(assetId)
                .map(asset -> "SUPERADMIN".equals(user.getRole()) || asset.getCreator().getId().equals(user.getId()))
                .orElse(true);
    }

    public record BulkUploadRequest(List<String> filenames, String type, UUID assetId, String folder) {}
}