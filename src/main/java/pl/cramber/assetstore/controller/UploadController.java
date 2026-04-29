package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.UserRepository;
import pl.cramber.assetstore.service.R2Service;

@RestController
@RequestMapping("/api/admin/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final R2Service r2Service;
    private final UserRepository userRepository;

    @GetMapping("/generate-image-url")
    public ResponseEntity<R2Service.ImageUploadTicket> getUploadUrl(
            @RequestParam String filename,
            @AuthenticationPrincipal OAuth2User principal) {

        User creator = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();

        return ResponseEntity.ok(r2Service.generateImageUploadUrl(creator.getId(), filename));
    }
}