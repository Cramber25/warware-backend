package pl.cramber.assetstore.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_order_user", columnList = "user_id"),
        @Index(name = "idx_order_status", columnList = "status"),
        @Index(name = "idx_order_type", columnList = "order_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    @Builder.Default
    private boolean downloaded = false;

    @Column(name = "order_type", nullable = false)
    @Builder.Default
    private String orderType = "PURCHASE";

    @Column(name = "payment_method", nullable = false)
    @Builder.Default
    private String paymentMethod = "ROBUX";

    @Column(name = "purchase_price", nullable = false)
    @Builder.Default
    private Integer purchasePrice = 0;

    @Column(name = "purchase_price_usd")
    private Double purchasePriceUsd;

    @Column(name = "paypal_order_id")
    private String paypalOrderId;

    @Column(name = "paypal_capture_id")
    private String paypalCaptureId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promo_code_id")
    private PromoCode promoCode;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}