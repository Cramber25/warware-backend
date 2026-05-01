package pl.cramber.assetstore.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pl.cramber.assetstore.entity.Asset;
import pl.cramber.assetstore.entity.AssetRating;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.AssetRatingRepository;
import pl.cramber.assetstore.repository.AssetRepository;
import pl.cramber.assetstore.repository.OrderRepository;
import pl.cramber.assetstore.repository.UserRepository;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/assets/{assetId}/ratings")
@RequiredArgsConstructor
public class AssetRatingController {

    private final AssetRatingRepository ratingRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    @Data
    public static class RatingRequest {
        private Integer rating;
        private String comment;
    }

    @Data
    public static class RatingResponse {
        private UUID id;
        private String username;
        private String avatarUrl;
        private Integer rating;
        private String comment;
        private ZonedDateTime createdAt;
        private String adminReply;
        private ZonedDateTime adminReplyCreatedAt;

        public RatingResponse(AssetRating rating) {
            this.id = rating.getId();
            this.username = rating.getUser().getDiscordUsername() != null ? rating.getUser().getDiscordUsername() : rating.getUser().getDiscordId();
            this.avatarUrl = rating.getUser().getDiscordAvatarUrl();
            this.rating = rating.getRating();
            this.comment = rating.getComment();
            this.createdAt = rating.getCreatedAt();
            this.adminReply = rating.getAdminReply();
            this.adminReplyCreatedAt = rating.getAdminReplyCreatedAt();
        }
    }

    @GetMapping
    public ResponseEntity<Page<RatingResponse>> getRatings(
            @PathVariable UUID assetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<RatingResponse> ratings = ratingRepository.findByAssetId(assetId, pageable)
                .map(RatingResponse::new);

        return ResponseEntity.ok(ratings);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> addRating(
            @PathVariable UUID assetId,
            @RequestBody RatingRequest request,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) return ResponseEntity.status(401).build();

        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            return ResponseEntity.badRequest().body(Map.of("error", "Rating must be between 1 and 5."));
        }

        if (request.getComment() != null && request.getComment().length() > 500) {
            return ResponseEntity.badRequest().body(Map.of("error", "Comment is too long (max 500 characters)."));
        }

        User user = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        Asset asset = assetRepository.findById(assetId).orElseThrow();

        if (!orderRepository.existsByUserIdAndAssetIdAndStatus(user.getId(), assetId, "COMPLETED")) {
            return ResponseEntity.badRequest().body(Map.of("error", "You must purchase and receive this asset before rating."));
        }

        if (ratingRepository.existsByAssetIdAndUserId(assetId, user.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "You have already rated this asset."));
        }

        AssetRating rating = AssetRating.builder()
                .asset(asset)
                .user(user)
                .rating(request.getRating())
                .comment(request.getComment())
                .build();
        ratingRepository.save(rating);

        int newCount = asset.getRatingCount() + 1;
        double newAverage = ((asset.getAverageRating() * asset.getRatingCount()) + request.getRating()) / newCount;

        asset.setRatingCount(newCount);
        asset.setAverageRating(newAverage);
        assetRepository.save(asset);

        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }

    @PostMapping("/{ratingId}/reply")
    @Transactional
    public ResponseEntity<?> replyToRating(
            @PathVariable UUID assetId,
            @PathVariable UUID ratingId,
            @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) return ResponseEntity.status(401).build();

        User admin = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        AssetRating rating = ratingRepository.findById(ratingId).orElseThrow();

        boolean isSuperAdmin = "SUPERADMIN".equals(admin.getRole());
        boolean isCreator = rating.getAsset().getCreator().getId().equals(admin.getId());

        if (!isSuperAdmin && !isCreator) {
            return ResponseEntity.status(403).body(Map.of("error", "Only the asset creator or a superadmin can reply."));
        }

        if (rating.getAdminReply() != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "This rating already has an admin reply."));
        }

        String replyText = payload.get("reply");
        if (replyText == null || replyText.trim().isEmpty() || replyText.length() > 1000) {
            return ResponseEntity.badRequest().body(Map.of("error", "Reply must be between 1 and 1000 characters."));
        }

        rating.setAdminReply(replyText);
        rating.setAdminReplyCreatedAt(ZonedDateTime.now());
        ratingRepository.save(rating);

        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }

    @DeleteMapping("/{ratingId}")
    @Transactional
    public ResponseEntity<?> deleteRating(
            @PathVariable UUID assetId,
            @PathVariable UUID ratingId,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) return ResponseEntity.status(401).build();

        User user = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();

        if (!"SUPERADMIN".equals(user.getRole())) {
            return ResponseEntity.status(403).body(Map.of("error", "Only superadmins can delete ratings."));
        }

        AssetRating rating = ratingRepository.findById(ratingId).orElseThrow();
        Asset asset = rating.getAsset();

        int newCount = asset.getRatingCount() - 1;
        double newAverage = (newCount == 0) ? 0.0 : ((asset.getAverageRating() * asset.getRatingCount()) - rating.getRating()) / newCount;

        asset.setRatingCount(newCount);
        asset.setAverageRating(newAverage);
        assetRepository.save(asset);

        ratingRepository.delete(rating);

        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }
}