package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.UserRepository;
import pl.cramber.assetstore.service.R2Service;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/migrate")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class AvatarMigrationController {

    private final UserRepository userRepository;
    private final R2Service r2Service;

    @PostMapping("/avatars")
    public ResponseEntity<String> migrateAvatars() {
        List<User> users = userRepository.findAll();
        int success = 0;
        int failed = 0;

        for (User user : users) {
            try {
                if (user.getRobloxAvatarUrl() != null && user.getRobloxAvatarUrl().contains("tr.rbxcdn.com")) {
                    String cdnUrl = r2Service.uploadAvatarFromUrl(user.getRobloxAvatarUrl(), "roblox", user.getRobloxId());
                    user.setRobloxAvatarUrl(cdnUrl);
                    userRepository.save(user);
                    log.info("roblox avatar migrated: {}", user.getRobloxId());
                    success++;
                }

                if (user.getDiscordAvatarUrl() != null && user.getDiscordAvatarUrl().contains("cdn.discordapp.com")) {
                    String cdnUrl = r2Service.uploadAvatarFromUrl(user.getDiscordAvatarUrl(), "discord", user.getDiscordId());
                    user.setDiscordAvatarUrl(cdnUrl);
                    userRepository.save(user);
                    log.info("discord avatar migrated: {}", user.getDiscordId());
                    success++;
                }
            } catch (Exception e) {
                log.error("failed for user {}: {}", user.getId(), e.getMessage());
                failed++;
            }
        }

        return ResponseEntity.ok("done. success=" + success + " failed=" + failed);
    }
}