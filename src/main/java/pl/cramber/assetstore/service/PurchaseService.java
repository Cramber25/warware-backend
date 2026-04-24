package pl.cramber.assetstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.cramber.assetstore.entity.*;
import pl.cramber.assetstore.repository.*;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final TransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeUsageRepository promoCodeUsageRepository;
    private final R2StorageService r2StorageService;

    @Transactional
    public String generateOneTimeDownload(UUID userId, UUID assetId) {
        Order order = orderRepository.findByUserIdAndAssetIdAndStatusNot(userId, assetId, "CANCELLED")
                .orElseThrow(() -> new RuntimeException("ORDER_NOT_FOUND"));

        if (!"COMPLETED".equals(order.getStatus())) {
            throw new RuntimeException("ORDER_NOT_COMPLETED");
        }

        if (order.isDownloaded()) {
            throw new RuntimeException("ALREADY_DOWNLOADED");
        }

        order.setDownloaded(true);
        orderRepository.save(order);

        return r2StorageService.generatePresignedUrl(order.getAsset().getR2FileKey(), 15);
    }

    @Transactional
    public String processPurchase(UUID userId, UUID assetId, String promoCodeName) {
        User user = userRepository.findByIdForUpdate(userId).orElseThrow();
        Asset asset = assetRepository.findById(assetId).orElseThrow();

        boolean alreadyOwned = orderRepository.findByUserIdAndAssetIdAndStatusNot(userId, assetId, "CANCELLED").isPresent();
        if (alreadyOwned) throw new RuntimeException("ALREADY_OWNED");

        int finalPrice = asset.getPrice();
        PromoCode usedCode = null;

        if (promoCodeName != null && !promoCodeName.isEmpty()) {
            PromoCode code = promoCodeRepository.findByCodeAndIsActiveTrue(promoCodeName)
                    .orElseThrow(() -> new RuntimeException("INVALID_PROMO_CODE"));

            if (code.getCreator() != null && !code.getCreator().getId().equals(asset.getCreator().getId())) {
                throw new RuntimeException("CODE_NOT_VALID_FOR_THIS_CREATOR");
            }
            if (code.getTargetAsset() != null && !code.getTargetAsset().getId().equals(assetId)) {
                throw new RuntimeException("CODE_NOT_VALID_FOR_THIS_ASSET");
            }
            if (code.getTargetCategory() != null && (asset.getCategory() == null || !code.getTargetCategory().getId().equals(asset.getCategory().getId()))) {
                throw new RuntimeException("CODE_NOT_VALID_FOR_THIS_CATEGORY");
            }
            if (code.getTargetTag() != null && asset.getTags().stream().noneMatch(t -> t.getId().equals(code.getTargetTag().getId()))) {
                throw new RuntimeException("CODE_NOT_VALID_FOR_THIS_TAG");
            }

            if (code.isPerUser() && promoCodeUsageRepository.existsByUserIdAndPromoCodeId(userId, code.getId())) {
                throw new RuntimeException("PROMO_CODE_ALREADY_USED");
            }
            if (code.getUsageLimit() != null && promoCodeUsageRepository.countByPromoCodeId(code.getId()) >= code.getUsageLimit()) {
                throw new RuntimeException("PROMO_CODE_EXHAUSTED");
            }

            if (code.getDiscountPercent() != null && code.getDiscountPercent() > 0) {
                finalPrice = finalPrice * (100 - code.getDiscountPercent()) / 100;
            } else if (code.getDiscountAmount() != null && code.getDiscountAmount() > 0) {
                finalPrice = finalPrice - code.getDiscountAmount();
            }

            if (finalPrice < 0) finalPrice = 0;
            usedCode = code;
        }

        if (user.getBalance() < finalPrice) throw new RuntimeException("INSUFFICIENT_BALANCE");

        user.setBalance(user.getBalance() - finalPrice);
        userRepository.save(user);

        Transaction transaction = Transaction.builder().user(user).amount(-finalPrice).type("PURCHASE").build();
        transactionRepository.save(transaction);

        Order order = Order.builder()
                .user(user)
                .asset(asset)
                .status("PENDING")
                .purchasePrice(finalPrice)
                .promoCode(usedCode)
                .build();

        if (usedCode != null) {
            promoCodeUsageRepository.save(PromoCodeUsage.builder().user(user).promoCode(usedCode).build());
        }

        boolean isTrusted = "TRUSTED".equals(user.getRole()) || "SUPERADMIN".equals(user.getRole());
        if ("INSTANT".equals(asset.getDeliveryType()) || isTrusted || finalPrice == 0) {
            order.setStatus("COMPLETED");
            orderRepository.save(order);
            return "SUCCESS";
        } else {
            orderRepository.save(order);
            ticketRepository.save(Ticket.builder().order(order).status("OPEN").build());
            return "VERIFICATION_REQUIRED";
        }
    }

    @Transactional
    public void grantAccess(UUID targetUserId, UUID assetId) {
        User user = userRepository.findById(targetUserId).orElseThrow();
        Asset asset = assetRepository.findById(assetId).orElseThrow();

        Optional<Order> existing = orderRepository.findByUserIdAndAssetIdAndStatusNot(targetUserId, assetId, "CANCELLED");
        if (existing.isPresent()) {
            Order order = existing.get();
            order.setDownloaded(false);
            orderRepository.save(order);
            return;
        }

        Order order = Order.builder()
                .user(user)
                .asset(asset)
                .status("COMPLETED")
                .downloaded(false)
                .orderType("GRANT")
                .purchasePrice(0)
                .build();
        orderRepository.save(order);
    }
}