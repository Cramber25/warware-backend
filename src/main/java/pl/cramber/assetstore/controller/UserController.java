package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.UserRepository;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @PostMapping("/link-roblox")
    public ResponseEntity<Void> linkRoblox(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody Map<String, String> payload) {

        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        String discordId = principal.getAttribute("id");
        String robloxId = payload.get("robloxId");

        User user = userRepository.findByDiscordId(discordId).orElseThrow();
        user.setRobloxId(robloxId);
        userRepository.save(user);

        return ResponseEntity.ok().build();
    }
}