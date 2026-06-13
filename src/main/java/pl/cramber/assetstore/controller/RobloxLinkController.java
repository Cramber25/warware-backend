package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.BlacklistEntryRepository;
import pl.cramber.assetstore.repository.UserRepository;
import pl.cramber.assetstore.service.R2Service;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth/roblox")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class RobloxLinkController {

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final BlacklistEntryRepository blacklistEntryRepository;
    private final R2Service r2Service;

    @Value("${ROBLOX_CLIENT_ID:}")
    private String clientId;

    @Value("${ROBLOX_CLIENT_SECRET:}")
    private String clientSecret;

    @Value("${FRONTEND_URL:http://localhost:5173}")
    private String frontendUrl;

    @Value("${BACKEND_URL:http://localhost:8080}")
    private String backendUrl;

    @GetMapping("/link")
    public ResponseEntity<Void> initiateRobloxLink() {
        String redirectUri = backendUrl + "/api/auth/roblox/callback";
        String authorizationUrl = "https://apis.roblox.com/oauth/v1/authorize" +
                "?client_id=" + clientId +
                "&redirect_uri=" + redirectUri +
                "&response_type=code" +
                "&scope=openid%20profile";

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(authorizationUrl))
                .build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> handleRobloxCallback(
            @RequestParam("code") String code,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendUrl + "?error=not_authenticated"))
                    .build();
        }

        RestTemplate restTemplate = new RestTemplate();
        String redirectUri = backendUrl + "/api/auth/roblox/callback";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", redirectUri);

        try {
            ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(
                    "https://apis.roblox.com/oauth/v1/token",
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            String accessToken = (String) tokenResponse.getBody().get("access_token");

            HttpHeaders userHeaders = new HttpHeaders();
            userHeaders.setBearerAuth(accessToken);
            ResponseEntity<Map> userResponse = restTemplate.exchange(
                    "https://apis.roblox.com/oauth/v1/userinfo",
                    HttpMethod.GET,
                    new HttpEntity<>(userHeaders),
                    Map.class
            );

            String robloxId = userResponse.getBody().get("sub").toString();
            String robloxUsername = (String) userResponse.getBody().get("preferred_username");
            String robloxAvatarUrl = (String) userResponse.getBody().get("picture");
            String discordId = principal.getAttribute("id");

            User user = userRepository.findByDiscordId(discordId).orElseThrow();

            if (blacklistEntryRepository.existsByRobloxIdAndIsActiveTrue(robloxId)) {
                user.setBanned(true);
                userRepository.save(user);
                return ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(frontendUrl + "/dashboard?error=blacklisted"))
                        .build();
            }

            Optional<User> existingRobloxUser = userRepository.findByRobloxId(robloxId);

            if (existingRobloxUser.isPresent()) {
                if (existingRobloxUser.get().isBanned()) {
                    user.setBanned(true);
                    userRepository.save(user);
                    return ResponseEntity.status(HttpStatus.FOUND)
                            .location(URI.create(frontendUrl + "/"))
                            .build();
                }

                if (!existingRobloxUser.get().getId().equals(user.getId())) {
                    return ResponseEntity.status(HttpStatus.FOUND)
                            .location(URI.create(frontendUrl + "/dashboard"))
                            .build();
                }
            }

            String finalRobloxAvatarUrl = r2Service.uploadAvatarFromUrl(robloxAvatarUrl.replaceAll("(.*/)Png(/noFilter.*)$", "$1Webp$2"), "roblox", robloxId);


            user.setRobloxId(robloxId);
            user.setRobloxUsername(robloxUsername);
            user.setRobloxAvatarUrl(finalRobloxAvatarUrl);
            userRepository.save(user);

            redisTemplate.convertAndSend("verification-channel", discordId + "::" + robloxUsername);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendUrl + "/dashboard?error=roblox_link_failed"))
                    .build();
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendUrl + "/dashboard"))
                .build();
    }
}