package pl.cramber.assetstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.cramber.assetstore.entity.*;
import pl.cramber.assetstore.repository.*;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class PurchaseService {

    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final TransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeUsageRepository promoCodeUsageRepository;
    private final R2StorageService r2StorageService;
    private final DiscordNotificationService discordNotificationService;
    private final PayPalService payPalService;

    private final UUID BONUS_ASSET_ID = UUID.fromString("4a8ec8a5-b5fd-4104-90aa-0e205c781b86");

    @Transactional
    public String generateOneTimeDownload(UUID userId, UUID assetId) {
        Order order = orderRepository.findByUserIdAndAssetIdAndStatusNot(userId, assetId, "CANCELLED")
                .orElseThrow(() -> new RuntimeException("ORDER_NOT_FOUND"));

        if (!"COMPLETED".equals(order.getStatus())) {
            throw new RuntimeException("ORDER_NOT_COMPLETED");
        }

        if ("ARCHIVED".equals(order.getAsset().getVisibility())) {
            throw new RuntimeException("ASSET_ARCHIVED_DOWNLOAD_BLOCKED");
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

        if (!"PUBLIC".equals(asset.getVisibility()) && !"UNLISTED".equals(asset.getVisibility())) {
            throw new RuntimeException("ASSET_UNAVAILABLE");
        }

        Optional<Order> existingOrder = orderRepository.findByUserIdAndAssetIdAndStatusNot(userId, assetId, "CANCELLED");
        if (existingOrder.isPresent()) {
            if (!"PENDING_PAYPAL".equals(existingOrder.get().getStatus())) {
                throw new RuntimeException("ALREADY_OWNED");
            }
        }

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

        Transaction transaction = Transaction.builder().user(user).amount(-finalPrice).amountUsd(0.0).currency("ROBUX").type("PURCHASE").build();
        transactionRepository.save(transaction);

        Order order = existingOrder.orElse(new Order());
        order.setUser(user);
        order.setAsset(asset);
        order.setStatus("PENDING");
        order.setPurchasePrice(finalPrice);
        order.setPurchasePriceUsd(0.0);
        order.setPaymentMethod("ROBUX");
        order.setPromoCode(usedCode);

        if (usedCode != null) {
            promoCodeUsageRepository.save(PromoCodeUsage.builder().user(user).promoCode(usedCode).build());
        }

        boolean isTrusted = "TRUSTED".equals(user.getRole()) || "SUPERADMIN".equals(user.getRole());
        String statusResult;

        if ("INSTANT".equals(asset.getDeliveryType()) || isTrusted || finalPrice == 0) {
            order.setStatus("COMPLETED");
            orderRepository.save(order);
            statusResult = "SUCCESS";
            discordNotificationService.sendOrderCompleteDM(user.getDiscordId(), asset.getTitle());
        } else {
            orderRepository.save(order);
            ticketRepository.save(Ticket.builder().order(order).status("OPEN").build());
            statusResult = "VERIFICATION_REQUIRED";
        }

        String username = user.getRobloxUsername() != null ? user.getRobloxUsername() : user.getDiscordUsername();
        String priceStr = finalPrice + " R$";
        discordNotificationService.sendPurchaseNotification(username, user.getDiscordId(), asset.getTitle(), asset.getId().toString(), "ROBUX", priceStr);

        return statusResult;
    }

    @Transactional
    public Map<String, String> createPaypalOrder(UUID userId, UUID assetId, String promoCodeName) {
        User user = userRepository.findByIdForUpdate(userId).orElseThrow();
        Asset asset = assetRepository.findById(assetId).orElseThrow();

        if (!Boolean.TRUE.equals(asset.getPaypalEnabled()) || asset.getPriceUsd() == null || asset.getPaypalEnvKey() == null) {
            throw new RuntimeException("PAYPAL_NOT_ENABLED_FOR_ASSET");
        }
        if (!"PUBLIC".equals(asset.getVisibility()) && !"UNLISTED".equals(asset.getVisibility())) {
            throw new RuntimeException("ASSET_UNAVAILABLE");
        }
        Optional<Order> existingOrder = orderRepository.findByUserIdAndAssetIdAndStatusNot(userId, assetId, "CANCELLED");
        if (existingOrder.isPresent()) {
            if (!"PENDING_PAYPAL".equals(existingOrder.get().getStatus())) {
                throw new RuntimeException("ALREADY_OWNED");
            }
        }

        Double finalPriceUsd = asset.getPriceUsd();
        PromoCode usedCode = null;

        if (promoCodeName != null && !promoCodeName.isEmpty()) {
            PromoCode code = promoCodeRepository.findByCodeAndIsActiveTrue(promoCodeName).orElseThrow(() -> new RuntimeException("INVALID_PROMO_CODE"));

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
                finalPriceUsd = finalPriceUsd * (100 - code.getDiscountPercent()) / 100.0;
            }
            if (finalPriceUsd < 0) finalPriceUsd = 0.0;
            usedCode = code;
        }

        if (finalPriceUsd == 0.0) {
            Order order = existingOrder.orElse(new Order());
            order.setUser(user);
            order.setAsset(asset);
            order.setPurchasePrice(0);
            order.setPurchasePriceUsd(0.0);
            order.setPaymentMethod("PAYPAL");
            order.setPromoCode(usedCode);

            if (usedCode != null) {
                promoCodeUsageRepository.save(PromoCodeUsage.builder().user(user).promoCode(usedCode).build());
            }

            Transaction transaction = Transaction.builder().user(user).amount(0).amountUsd(0.0).currency("USD").type("PURCHASE").build();
            transactionRepository.save(transaction);

            boolean isTrusted = "TRUSTED".equals(user.getRole()) || "SUPERADMIN".equals(user.getRole());
            String statusResult;

            if ("INSTANT".equals(asset.getDeliveryType()) || isTrusted) {
                order.setStatus("COMPLETED");
                statusResult = "SUCCESS";
                discordNotificationService.sendOrderCompleteDM(user.getDiscordId(), asset.getTitle());
            } else {
                order.setStatus("PENDING");
                ticketRepository.save(Ticket.builder().order(order).status("OPEN").build());
                statusResult = "VERIFICATION_REQUIRED";
            }
            orderRepository.save(order);

            String username = user.getRobloxUsername() != null ? user.getRobloxUsername() : user.getDiscordUsername();
            discordNotificationService.sendPurchaseNotification(username, user.getDiscordId(), asset.getTitle(), asset.getId().toString(), "PAYPAL", "$0.00");

            if (!assetId.equals(BONUS_ASSET_ID)) {
                try { grantAccess(userId, BONUS_ASSET_ID); } catch (Exception ignored) {}
            }
            return Map.of("status", statusResult);
        }

        Map<String, String> paypalData = payPalService.createOrder(asset.getPaypalEnvKey(), finalPriceUsd, "USD");

        Order order = existingOrder.orElse(new Order());
        order.setUser(user);
        order.setAsset(asset);
        order.setStatus("PENDING_PAYPAL");
        order.setPurchasePrice(0);
        order.setPurchasePriceUsd(finalPriceUsd);
        order.setPaymentMethod("PAYPAL");
        order.setPaypalOrderId(paypalData.get("orderId"));
        order.setPromoCode(usedCode);

        orderRepository.save(order);
        return paypalData;
    }

    @Transactional
    public String capturePaypalOrder(UUID userId, UUID assetId, String paypalOrderId) {
        Order order = orderRepository.findByUserIdAndAssetIdAndStatusNot(userId, assetId, "CANCELLED")
                .stream().filter(o -> "PENDING_PAYPAL".equals(o.getStatus()) && paypalOrderId.equals(o.getPaypalOrderId()))
                .findFirst().orElseThrow(() -> new RuntimeException("ORDER_NOT_FOUND"));

        Map<String, String> captureData = payPalService.captureOrder(order.getAsset().getPaypalEnvKey(), paypalOrderId);
        if (!"COMPLETED".equals(captureData.get("status"))) {
            throw new RuntimeException("PAYPAL_CAPTURE_FAILED");
        }

        order.setPaypalCaptureId(captureData.get("captureId"));

        if (order.getPromoCode() != null) {
            promoCodeUsageRepository.save(PromoCodeUsage.builder().user(order.getUser()).promoCode(order.getPromoCode()).build());
        }

        Transaction transaction = Transaction.builder().user(order.getUser()).amount(0).amountUsd(-order.getPurchasePriceUsd()).currency("USD").type("PURCHASE").build();
        transactionRepository.save(transaction);

        boolean isTrusted = "TRUSTED".equals(order.getUser().getRole()) || "SUPERADMIN".equals(order.getUser().getRole());
        String statusResult;

        if ("INSTANT".equals(order.getAsset().getDeliveryType()) || isTrusted || order.getPurchasePriceUsd() == 0.0) {
            order.setStatus("COMPLETED");
            statusResult = "SUCCESS";
            discordNotificationService.sendOrderCompleteDM(order.getUser().getDiscordId(), order.getAsset().getTitle());
        } else {
            order.setStatus("PENDING");
            ticketRepository.save(Ticket.builder().order(order).status("OPEN").build());
            statusResult = "VERIFICATION_REQUIRED";
        }
        orderRepository.save(order);

        String username = order.getUser().getRobloxUsername() != null ? order.getUser().getRobloxUsername() : order.getUser().getDiscordUsername();
        String priceStr = "$" + String.format(Locale.US, "%.2f", order.getPurchasePriceUsd());
        discordNotificationService.sendPurchaseNotification(username, order.getUser().getDiscordId(), order.getAsset().getTitle(), order.getAsset().getId().toString(), "PAYPAL", priceStr);

        if (!assetId.equals(BONUS_ASSET_ID)) {
            try { grantAccess(userId, BONUS_ASSET_ID); } catch (Exception ignored) {}
        }

        return statusResult;
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
                .paymentMethod("ROBUX")
                .purchasePrice(0)
                .purchasePriceUsd(0.0)
                .build();
        orderRepository.save(order);
    }
}