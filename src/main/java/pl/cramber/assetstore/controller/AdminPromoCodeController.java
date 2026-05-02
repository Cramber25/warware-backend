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
import pl.cramber.assetstore.dto.PromoCodeResponse;
import pl.cramber.assetstore.entity.*;
import pl.cramber.assetstore.repository.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/promocodes")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class AdminPromoCodeController {

    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeUsageRepository promoCodeUsageRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final AuditLogRepository auditLogRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Page<PromoCodeResponse>> getPromoCodes(
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

        Page<PromoCode> codes = "SUPERADMIN".equals(admin.getRole())
                ? promoCodeRepository.searchPromoCodes(safeSearch, pageable)
                : promoCodeRepository.searchCreatorPromoCodes(admin.getId(), safeSearch, pageable);

        Page<PromoCodeResponse> mappedCodes = codes.map(code -> mapToDto(code, promoCodeUsageRepository.countByPromoCodeId(code.getId())));
        return ResponseEntity.ok(mappedCodes);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<PromoCodeResponse> createPromoCode(@RequestBody Map<String, Object> payload, @AuthenticationPrincipal OAuth2User principal) {
        User admin = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();

        PromoCode code = new PromoCode();
        code.setCode(payload.get("code").toString().toUpperCase());

        if (payload.get("discountPercent") != null) {
            code.setDiscountPercent(Integer.parseInt(payload.get("discountPercent").toString()));
        } else if (payload.get("discountAmount") != null) {
            code.setDiscountAmount(Integer.parseInt(payload.get("discountAmount").toString()));
        }

        code.setPerUser(Boolean.parseBoolean(payload.get("isPerUser").toString()));
        code.setActive(true);
        code.setArchived(false);
        code.setCreator(admin);

        if (payload.get("usageLimit") != null) {
            code.setUsageLimit(Integer.parseInt(payload.get("usageLimit").toString()));
        }

        if (payload.get("targetAssetId") != null) {
            Asset asset = assetRepository.findById(UUID.fromString(payload.get("targetAssetId").toString())).orElseThrow();
            if (!"SUPERADMIN".equals(admin.getRole()) && !asset.getCreator().getId().equals(admin.getId())) {
                return ResponseEntity.status(403).build();
            }
            code.setTargetAsset(asset);
        }
        if (payload.get("targetCategoryId") != null) {
            code.setTargetCategory(categoryRepository.findById(UUID.fromString(payload.get("targetCategoryId").toString())).orElse(null));
        }
        if (payload.get("targetTagId") != null) {
            code.setTargetTag(tagRepository.findById(UUID.fromString(payload.get("targetTagId").toString())).orElse(null));
        }

        PromoCode saved = promoCodeRepository.save(code);
        auditLogRepository.save(AuditLog.builder()
                .adminDiscordId(admin.getDiscordId())
                .action("CREATE_PROMO")
                .details("Created promo code: " + saved.getCode())
                .build());

        return ResponseEntity.ok(mapToDto(saved, 0L));
    }

    @PatchMapping("/{id}/toggle")
    @Transactional
    public ResponseEntity<PromoCodeResponse> togglePromoCode(
            @PathVariable UUID id,
            @AuthenticationPrincipal OAuth2User principal) {

        User admin = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        PromoCode code = promoCodeRepository.findById(id).orElseThrow();

        if (!"SUPERADMIN".equals(admin.getRole()) && !code.getCreator().getId().equals(admin.getId())) {
            return ResponseEntity.status(403).build();
        }

        code.setActive(!code.isActive());
        PromoCode saved = promoCodeRepository.save(code);

        auditLogRepository.save(AuditLog.builder()
                .adminDiscordId(admin.getDiscordId())
                .action("TOGGLE_PROMO")
                .details("Toggled promo code " + code.getCode())
                .build());

        long usageCount = promoCodeUsageRepository.countByPromoCodeId(code.getId());
        return ResponseEntity.ok(mapToDto(saved, usageCount));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deletePromoCode(
            @PathVariable UUID id,
            @AuthenticationPrincipal OAuth2User principal) {

        User admin = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        PromoCode code = promoCodeRepository.findById(id).orElseThrow();

        if (!"SUPERADMIN".equals(admin.getRole()) && !code.getCreator().getId().equals(admin.getId())) {
            return ResponseEntity.status(403).build();
        }

        code.setArchived(true);
        code.setActive(false);
        promoCodeRepository.save(code);

        auditLogRepository.save(AuditLog.builder()
                .adminDiscordId(admin.getDiscordId())
                .action("ARCHIVE_PROMO")
                .details("Archived promo code: " + code.getCode())
                .build());

        return ResponseEntity.ok().build();
    }

    private PromoCodeResponse mapToDto(PromoCode p, long usageCount) {
        return new PromoCodeResponse(
                p.getId(),
                p.getCode(),
                p.getDiscountPercent(),
                p.getDiscountAmount(),
                p.getUsageLimit(),
                usageCount,
                p.isPerUser(),
                p.isActive(),
                p.getCreator() != null ? new PromoCodeResponse.CreatorDto(p.getCreator().getId(), p.getCreator().getDiscordUsername() != null ? p.getCreator().getDiscordUsername() : "") : null,
                p.getTargetAsset() != null ? new PromoCodeResponse.TargetAssetDto(p.getTargetAsset().getId(), p.getTargetAsset().getTitle()) : null,
                p.getTargetCategory() != null ? new PromoCodeResponse.TargetCategoryDto(p.getTargetCategory().getId(), p.getTargetCategory().getName()) : null,
                p.getTargetTag() != null ? new PromoCodeResponse.TargetTagDto(p.getTargetTag().getId(), p.getTargetTag().getName()) : null
        );
    }
}