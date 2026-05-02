package pl.cramber.assetstore.service;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import pl.cramber.assetstore.bot.BotManager;
import pl.cramber.assetstore.entity.ModerationLog;
import pl.cramber.assetstore.repository.ModerationLogRepository;

import java.awt.Color;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "true")
public class ModerationService {

    private final ModerationLogRepository moderationLogRepository;
    private final StoreSettingService storeSettingService;
    private final BotManager botManager;

    public ModerationService(
            ModerationLogRepository moderationLogRepository,
            StoreSettingService storeSettingService,
            @Lazy BotManager botManager) {
        this.moderationLogRepository = moderationLogRepository;
        this.storeSettingService = storeSettingService;
        this.botManager = botManager;
    }

    public boolean canModerate(String executorRole, String targetRole) {
        return getRoleWeight(executorRole) > getRoleWeight(targetRole);
    }

    private int getRoleWeight(String role) {
        if (role == null) return 0;
        return switch (role.toUpperCase()) {
            case "SUPERADMIN" -> 100;
            case "ADMIN" -> 80;
            case "MODERATOR" -> 50;
            case "TRUSTED" -> 10;
            default -> 0;
        };
    }

    public void logAndNotify(User target, User moderator, String action, String reason, String duration) {
        ModerationLog log = ModerationLog.builder()
                .targetDiscordId(target.getId())
                .targetDiscordUsername(target.getName())
                .moderatorDiscordId(moderator.getId())
                .action(action)
                .reason(reason)
                .duration(duration)
                .build();
        moderationLogRepository.save(log);

        EmbedBuilder dmEmbed = new EmbedBuilder()
                .setTitle("Moderation Notice")
                .setColor(Color.RED)
                .addField("Action", action, true)
                .addField("Reason", reason != null ? reason : "No reason provided", false);

        if (duration != null) {
            dmEmbed.addField("Duration", duration, true);
        }

        target.openPrivateChannel().queue(
                ch -> ch.sendMessageEmbeds(dmEmbed.build()).queue(null, err -> {}),
                err -> {}
        );

        Map<String, String> settings = storeSettingService.getAllSettings();
        String logChannelId = settings.get("DISCORD_MOD_LOG_CHANNEL_ID");
        String guildId = settings.get("DISCORD_GUILD_ID");

        if (logChannelId != null && !logChannelId.isEmpty() && guildId != null && !guildId.isEmpty()) {
            JDA jda = botManager.getJda();
            if (jda != null) {
                Guild guild = jda.getGuildById(guildId);
                if (guild != null) {
                    TextChannel channel = guild.getTextChannelById(logChannelId);
                    if (channel != null) {
                        EmbedBuilder logEmbed = new EmbedBuilder()
                                .setTitle("Moderation Action: " + action)
                                .setColor(Color.ORANGE)
                                .addField("Target", target.getAsMention() + " (`" + target.getId() + "`)", true)
                                .addField("Moderator", moderator.getAsMention(), true)
                                .addField("Reason", reason != null ? reason : "No reason provided", false);

                        if (duration != null) {
                            logEmbed.addField("Duration", duration, true);
                        }

                        channel.sendMessageEmbeds(logEmbed.build()).queue();
                    }
                }
            }
        }
    }
}