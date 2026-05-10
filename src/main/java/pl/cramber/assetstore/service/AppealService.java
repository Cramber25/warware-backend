package pl.cramber.assetstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.cramber.assetstore.dto.AppealDto;
import pl.cramber.assetstore.entity.Appeal;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.AppealRepository;
import pl.cramber.assetstore.repository.BlacklistEntryRepository;
import pl.cramber.assetstore.repository.TempBanRepository;
import pl.cramber.assetstore.repository.UserRepository;

import java.time.ZonedDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class AppealService {

    private final AppealRepository appealRepository;
    private final UserRepository userRepository;
    private final BlacklistEntryRepository blacklistEntryRepository;
    private final TempBanRepository tempBanRepository;
    private final DiscordNotificationService discordNotificationService;

    @Transactional(readOnly = true)
    public Page<AppealDto> getAllAppeals(String status, Pageable pageable) {
        if (status != null && !status.isEmpty()) {
            return appealRepository.findByStatus(status, pageable).map(AppealDto::fromEntity);
        }
        return appealRepository.findAll(pageable).map(AppealDto::fromEntity);
    }

    @Transactional
    public AppealDto submitAppeal(User user, String type, UUID referenceId, String content) {
        if (!user.isBanned()) {
            throw new RuntimeException("USER_NOT_BANNED");
        }

        if (referenceId == null) {
            throw new RuntimeException("REFERENCE_ID_REQUIRED");
        }

        if (appealRepository.existsByReferenceId(referenceId)) {
            throw new RuntimeException("APPEAL_ALREADY_EXISTS_FOR_THIS_PENALTY");
        }

        if ("BLACKLIST".equals(type)) {
            boolean exists = blacklistEntryRepository.findById(referenceId)
                    .map(b -> b.getDiscordId().equals(user.getDiscordId()) && b.isActive())
                    .orElse(false);
            if (!exists) throw new RuntimeException("INVALID_BLACKLIST_REFERENCE");
        } else if ("BAN".equals(type)) {
            boolean exists = tempBanRepository.findById(referenceId)
                    .map(b -> b.getDiscordId().equals(user.getDiscordId()))
                    .orElse(false);
            if (!exists) throw new RuntimeException("INVALID_BAN_REFERENCE");
        } else {
            throw new RuntimeException("INVALID_APPEAL_TYPE");
        }

        Appeal appeal = Appeal.builder()
                .user(user)
                .appealType(type)
                .referenceId(referenceId)
                .content(content)
                .status("PENDING")
                .build();

        Appeal saved = appealRepository.save(appeal);

        String username = user.getRobloxUsername() != null ? user.getRobloxUsername() : user.getDiscordUsername();
        discordNotificationService.sendAppealNotification(username, user.getDiscordId(), type, content);

        return AppealDto.fromEntity(saved);
    }

    @Transactional
    public AppealDto resolveAppeal(UUID appealId, User admin, String action, String reply) {
        Appeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new RuntimeException("APPEAL_NOT_FOUND"));

        if (!"PENDING".equals(appeal.getStatus())) {
            throw new RuntimeException("APPEAL_ALREADY_RESOLVED");
        }

        appeal.setStatus(action);
        appeal.setAdminReply(reply);
        appeal.setResolvedBy(admin);
        appeal.setResolvedAt(ZonedDateTime.now());

        if ("ACCEPT".equals(action)) {
            User user = appeal.getUser();
            user.setBanned(false);
            userRepository.save(user);

            discordNotificationService.revokeDiscordBan(user.getDiscordId());
            tempBanRepository.deleteByDiscordId(user.getDiscordId());

            if ("BLACKLIST".equals(appeal.getAppealType())) {
                blacklistEntryRepository.findByDiscordIdAndIsActiveTrue(user.getDiscordId())
                        .ifPresent(entry -> {
                            entry.setActive(false);
                            blacklistEntryRepository.save(entry);
                        });
            }
        }

        return AppealDto.fromEntity(appealRepository.save(appeal));
    }
}