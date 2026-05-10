package pl.cramber.assetstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.BlacklistEntryRepository;
import pl.cramber.assetstore.repository.TempBanRepository;
import pl.cramber.assetstore.repository.UserRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class UserSyncService {

    private final UserRepository userRepository;
    private final TempBanRepository tempBanRepository;
    private final BlacklistEntryRepository blacklistEntryRepository;

    @Transactional
    public User syncUserWithPenalties(String discordId, String username, String finalAvatarUrl, String email) {

        boolean hasActiveBan = !tempBanRepository.findAllByDiscordIdAndIsActiveTrue(discordId).isEmpty();
        boolean hasActiveBlacklist = !blacklistEntryRepository.findAllByDiscordIdAndIsActiveTrue(discordId).isEmpty();
        boolean shouldBeBanned = hasActiveBan || hasActiveBlacklist;

        Optional<User> existingUserOpt = userRepository.findByDiscordId(discordId);

        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            existingUser.setDiscordUsername(username);
            existingUser.setDiscordAvatarUrl(finalAvatarUrl);
            existingUser.setEmail(email);
            existingUser.setBanned(shouldBeBanned);
            return userRepository.save(existingUser);
        } else {
            User newUser = User.builder()
                    .discordId(discordId)
                    .discordUsername(username)
                    .discordAvatarUrl(finalAvatarUrl)
                    .email(email)
                    .banned(shouldBeBanned)
                    .build();
            return userRepository.save(newUser);
        }
    }
}