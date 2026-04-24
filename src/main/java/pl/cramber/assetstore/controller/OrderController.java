package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.OrderRepository;
import pl.cramber.assetstore.repository.UserRepository;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @GetMapping("/my")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMyOrders(@AuthenticationPrincipal OAuth2User principal) {
        User user = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        var orders = orderRepository.findByUserId(user.getId()).stream().map(o -> Map.of(
                "id", o.getId(),
                "status", o.getStatus(),
                "downloaded", o.isDownloaded(),
                "createdAt", o.getCreatedAt(),
                "asset", Map.of(
                        "id", o.getAsset().getId(),
                        "title", o.getAsset().getTitle(),
                        "thumbnailUrl", o.getAsset().getThumbnailUrl() != null ? o.getAsset().getThumbnailUrl() : ""
                )
        )).collect(Collectors.toList());

        return ResponseEntity.ok(orders);
    }
}