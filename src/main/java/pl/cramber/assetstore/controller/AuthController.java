package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.UserRepository;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class AuthController {

    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return ResponseEntity.status(401)
                    .cacheControl(CacheControl.noCache().mustRevalidate())
                    .build();
        }

        String discordId = principal.getAttribute("id");

        return userRepository.findByDiscordId(discordId)
                .map(user -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noCache().mustRevalidate())
                        .body(user))
                .orElse(ResponseEntity.status(401)
                        .cacheControl(CacheControl.noCache().mustRevalidate())
                        .build());
    }
}