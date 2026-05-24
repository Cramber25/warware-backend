package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.cramber.assetstore.entity.Asset;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.AssetRepository;
import pl.cramber.assetstore.repository.UserRepository;
import pl.cramber.assetstore.service.R2Service;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class AdminController {

    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final R2Service r2Service;

    @GetMapping("/my-assets")
    public ResponseEntity<List<Asset>> getMyAssets(@AuthenticationPrincipal OAuth2User principal) {
        User admin = userRepository.findByDiscordId(principal.getAttribute("id")).orElseThrow();
        return ResponseEntity.ok(assetRepository.findAllByCreatorId(admin.getId()));
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        String fileKey = r2Service.uploadFile(file);
        return ResponseEntity.ok(fileKey);
    }
}