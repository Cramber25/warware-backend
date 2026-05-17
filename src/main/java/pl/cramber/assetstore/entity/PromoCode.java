package pl.cramber.assetstore.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "promo_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromoCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "discount_percent")
    private Integer discountPercent;

    @Column(name = "discount_amount")
    private Integer discountAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_asset_id")
    private Asset targetAsset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_category_id")
    private Category targetCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_tag_id")
    private Tag targetTag;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "current_usage", nullable = false)
    @Builder.Default
    private Integer currentUsage = 0;

    @Column(name = "is_per_user", nullable = false)
    private boolean isPerUser;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "is_archived", nullable = false)
    private boolean isArchived;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}