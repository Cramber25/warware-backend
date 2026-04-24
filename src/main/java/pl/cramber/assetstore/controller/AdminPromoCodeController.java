package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import pl.cramber.assetstore.entity.*;
import pl.cramber.assetstore.repository.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/promocodes")
@RequiredArgsConstructor
public class AdminPromoCodeController {

    private final PromoCodeRepository promoCodeRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public List<PromoCode> getPromoCodes(@AuthenticationPrincipal OAuth2User principal) {
        User admin = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        if ("SUPERADMIN".equals(admin.getRole())) {
            return promoCodeRepository.findAll();
        }
        return promoCodeRepository.findByCreatorId(admin.getId());
    }

    @PostMapping
    public ResponseEntity<?> createPromoCode(@RequestBody Map<String, Object> payload, @AuthenticationPrincipal OAuth2User principal) {
        User admin = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();

        PromoCode code = new PromoCode();
        code.setCode(payload.get("code").toString().toUpperCase());

        if (payload.containsKey("discountPercent") && payload.get("discountPercent") != null) {
            code.setDiscountPercent(Integer.parseInt(payload.get("discountPercent").toString()));
        } else if (payload.containsKey("discountAmount") && payload.get("discountAmount") != null) {
            code.setDiscountAmount(Integer.parseInt(payload.get("discountAmount").toString()));
        }

        code.setPerUser(Boolean.parseBoolean(payload.get("isPerUser").toString()));
        code.setActive(true);

        if (payload.containsKey("usageLimit") && payload.get("usageLimit") != null) {
            code.setUsageLimit(Integer.parseInt(payload.get("usageLimit").toString()));
        }

        if ("ADMIN".equals(admin.getRole())) {
            code.setCreator(admin);
        }

        if (payload.containsKey("targetAssetId") && payload.get("targetAssetId") != null) {
            code.setTargetAsset(assetRepository.findById(UUID.fromString(payload.get("targetAssetId").toString())).orElse(null));
        }
        if (payload.containsKey("targetCategoryId") && payload.get("targetCategoryId") != null) {
            code.setTargetCategory(categoryRepository.findById(UUID.fromString(payload.get("targetCategoryId").toString())).orElse(null));
        }
        if (payload.containsKey("targetTagId") && payload.get("targetTagId") != null) {
            code.setTargetTag(tagRepository.findById(UUID.fromString(payload.get("targetTagId").toString())).orElse(null));
        }

        PromoCode saved = promoCodeRepository.save(code);
        auditLogRepository.save(AuditLog.builder().adminDiscordId(admin.getDiscordId()).action("CREATE_PROMO").details("Created promo code: " + saved.getCode()).build());
        return ResponseEntity.ok(saved);
    }
}