package pl.cramber.assetstore.service;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.UserSnowflake;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.cramber.assetstore.bot.BotManager;
import pl.cramber.assetstore.entity.TempBan;
import pl.cramber.assetstore.repository.TempBanRepository;

import java.time.ZonedDateTime;
import java.util.List;

@Service
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "true")
public class TempBanScheduler {

    private final TempBanRepository tempBanRepository;
    private final BotManager botManager;
    private final ModerationService moderationService;

    @Scheduled(fixedRate = 30000)
    @Transactional
    public void processExpiredBans() {
        JDA jda = botManager.getJda();
        if (jda == null) return;

        List<TempBan> expiredBans = tempBanRepository.findAllByUnbanAtBefore(ZonedDateTime.now());

        for (TempBan ban : expiredBans) {
            Guild guild = jda.getGuildById(ban.getGuildId());
            if (guild != null) {
                guild.unban(UserSnowflake.fromId(ban.getDiscordId())).reason("Temporary ban expired").queue(
                        success -> logUnban(ban),
                        error -> logUnban(ban)
                );
            }
            tempBanRepository.delete(ban);
        }
    }

    private void logUnban(TempBan ban) {
        JDA jda = botManager.getJda();
        if (jda == null) return;

        String targetMention = "<@" + ban.getDiscordId() + ">";
        moderationService.logWithoutDM(
                ban.getDiscordId(),
                "Unknown",
                targetMention,
                jda.getSelfUser(),
                "UNBAN (AUTO)",
                "Temporary ban expired",
                null
        );
    }
}