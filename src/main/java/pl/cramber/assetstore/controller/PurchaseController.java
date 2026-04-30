package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.UserRepository;
import pl.cramber.assetstore.service.PurchaseService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;
    private final UserRepository userRepository;

    private final UUID BONUS_ASSET_ID = UUID.fromString("4a8ec8a5-b5fd-4104-90aa-0e205c781b86");

    @PostMapping("/{assetId}")
    public ResponseEntity<?> purchaseAsset(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable UUID assetId,
            @RequestParam(required = false) String promoCode) {

        User user = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        String result = purchaseService.processPurchase(user.getId(), assetId, promoCode);

        if ("VERIFICATION_REQUIRED".equals(result)) {
            return ResponseEntity.ok(Map.of("status", "PENDING", "message", "Ticket has been created for verification."));
        }

        if (!assetId.equals(BONUS_ASSET_ID)) {
            try {
                purchaseService.grantAccess(user.getId(), BONUS_ASSET_ID);
            } catch (Exception e) {}
        }

        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }

    @PostMapping("/{assetId}/download")
    public ResponseEntity<?> downloadAsset(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable UUID assetId) {

        User user = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        String url = purchaseService.generateOneTimeDownload(user.getId(), assetId);

        return ResponseEntity.ok(Map.of("downloadUrl", url));
    }
}