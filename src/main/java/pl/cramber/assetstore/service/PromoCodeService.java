package pl.cramber.assetstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pl.cramber.assetstore.entity.Asset;
import pl.cramber.assetstore.entity.PromoCode;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.AssetRepository;
import pl.cramber.assetstore.repository.PromoCodeRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;
    private final AssetRepository assetRepository;

    public PromoCode createPromoCode(User creator, PromoCode promoCode, UUID targetAssetId) {
        if ("ADMIN".equals(creator.getRole())) {
            Asset asset = assetRepository.findById(targetAssetId)
                    .orElseThrow(() -> new RuntimeException("ASSET_NOT_FOUND"));

            if (!asset.getCreator().getId().equals(creator.getId())) {
                throw new RuntimeException("UNAUTHORIZED_ASSET_PROMO");
            }
            promoCode.setTargetAsset(asset);
        } else if ("SUPERADMIN".equals(creator.getRole()) && targetAssetId != null) {
            Asset asset = assetRepository.findById(targetAssetId).orElse(null);
            promoCode.setTargetAsset(asset);
        }

        promoCode.setCreator(creator);
        return promoCodeRepository.save(promoCode);
    }
}