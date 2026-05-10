package pl.cramber.assetstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.cramber.assetstore.dto.AppealDto;
import pl.cramber.assetstore.dto.PenaltyDto;
import pl.cramber.assetstore.entity.Appeal;
import pl.cramber.assetstore.entity.User;
import pl.cramber.assetstore.repository.AppealRepository;
import pl.cramber.assetstore.repository.BlacklistEntryRepository;
import pl.cramber.assetstore.repository.TempBanRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class PenaltyService {

    private final BlacklistEntryRepository blacklistEntryRepository;
    private final TempBanRepository tempBanRepository;
    private final AppealRepository appealRepository;

    @Transactional(readOnly = true)
    public List<PenaltyDto> getUserPenalties(User user) {
        List<PenaltyDto> penalties = new ArrayList<>();

        blacklistEntryRepository.findAllByDiscordIdOrderByCreatedAtDesc(user.getDiscordId()).forEach(blacklist -> {
            Appeal appeal = appealRepository.findByReferenceId(blacklist.getId()).orElse(null);
            penalties.add(PenaltyDto.builder()
                    .id(blacklist.getId())
                    .type("BLACKLIST")
                    .reason(blacklist.getReason())
                    .expiresAt(null)
                    .createdAt(blacklist.getCreatedAt())
                    .isActive(blacklist.isActive())
                    .canAppeal(blacklist.isActive() && appeal == null)
                    .appeal(appeal != null ? AppealDto.fromEntity(appeal) : null)
                    .build());
        });

        tempBanRepository.findAllByDiscordIdOrderByCreatedAtDesc(user.getDiscordId()).forEach(ban -> {
            Appeal appeal = appealRepository.findByReferenceId(ban.getId()).orElse(null);
            penalties.add(PenaltyDto.builder()
                    .id(ban.getId())
                    .type("BAN")
                    .reason(ban.getReason() != null ? ban.getReason() : "Discord Server Ban")
                    .expiresAt(ban.getUnbanAt())
                    .createdAt(ban.getCreatedAt())
                    .isActive(ban.isActive())
                    .canAppeal(ban.isActive() && appeal == null)
                    .appeal(appeal != null ? AppealDto.fromEntity(appeal) : null)
                    .build());
        });

        return penalties;
    }
}