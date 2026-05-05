package pl.cramber.assetstore.bot.commands;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.cramber.assetstore.entity.TempBan;
import pl.cramber.assetstore.repository.TempBanRepository;
import pl.cramber.assetstore.repository.UserRepository;
import pl.cramber.assetstore.service.ModerationService;

import java.awt.Color;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "true")
@RequiredArgsConstructor
public class BanIdCommand implements BotCommand {

    private final UserRepository userRepository;
    private final TempBanRepository tempBanRepository;
    private final ModerationService moderationService;

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("ban_id", "Bans a user from the server by their Discord ID.")
                .addOption(OptionType.STRING, "discord_id", "Discord User ID", true)
                .addOption(OptionType.STRING, "reason", "Reason for banning", false)
                .addOption(OptionType.STRING, "time", "Duration (e.g. 1d, 1w, 1M)", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        String executorId = event.getUser().getId();
        Optional<pl.cramber.assetstore.entity.User> executorOpt = userRepository.findByDiscordId(executorId);

        if (executorOpt.isEmpty()) {
            event.getHook().deleteOriginal().queue();
            event.getHook().sendMessage("You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        String executorRole = executorOpt.get().getRole();
        if (!"SUPERADMIN".equals(executorRole) && !"ADMIN".equals(executorRole) && !"MODERATOR".equals(executorRole)) {
            event.getHook().deleteOriginal().queue();
            event.getHook().sendMessage("You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        String targetId = event.getOption("discord_id").getAsString();
        String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString() : "No reason provided";
        String timeStr = event.getOption("time") != null ? event.getOption("time").getAsString() : null;

        Duration duration = parseDuration(timeStr);
        if (timeStr != null && duration == null) {
            event.getHook().deleteOriginal().queue();
            event.getHook().sendMessage("Invalid time format. Please use formats like `1h`, `1d`, `1w`.").setEphemeral(true).queue();
            return;
        }

        ZonedDateTime unbanAt = duration != null ? ZonedDateTime.now().plus(duration) : null;
        String durationDisplay = timeStr != null ? timeStr : "PERMANENT";

        Runnable executeBanAndLog = () -> {
            event.getGuild().ban(UserSnowflake.fromId(targetId), 0, TimeUnit.DAYS).reason(reason).queue(
                    success -> {
                        if (unbanAt != null) {
                            tempBanRepository.save(TempBan.builder()
                                    .discordId(targetId)
                                    .guildId(event.getGuild().getId())
                                    .unbanAt(unbanAt)
                                    .build());
                        }

                        moderationService.logWithoutDM(targetId, "Unknown", "<@" + targetId + ">", event.getUser(), "BAN", reason, durationDisplay);

                        EmbedBuilder embed = new EmbedBuilder()
                                .setColor(Color.RED)
                                .setDescription("Successfully banned <@" + targetId + "> for " + durationDisplay + ".");
                        event.getHook().editOriginalEmbeds(embed.build()).queue();
                    },
                    error -> {
                        event.getHook().deleteOriginal().queue();
                        event.getHook().sendMessage("Failed to ban user ID `" + targetId + "`. Check my role hierarchy or verify if the ID is valid.").setEphemeral(true).queue();
                    }
            );
        };

        event.getJDA().retrieveUserById(targetId).queue(
                user -> {
                    EmbedBuilder dmEmbed = new EmbedBuilder()
                            .setTitle("Moderation Notice")
                            .setColor(Color.RED)
                            .addField("Action", "BAN", true)
                            .addField("Reason", reason, false)
                            .addField("Duration", durationDisplay, true);

                    user.openPrivateChannel().queue(
                            ch -> ch.sendMessageEmbeds(dmEmbed.build()).queue(s -> executeBanAndLog.run(), e -> executeBanAndLog.run()),
                            e -> executeBanAndLog.run()
                    );
                },
                error -> executeBanAndLog.run()
        );
    }

    private Duration parseDuration(String input) {
        if (input == null || input.isEmpty()) return null;
        try {
            char unit = input.charAt(input.length() - 1);
            long amount = Long.parseLong(input.substring(0, input.length() - 1));
            return switch (unit) {
                case 's' -> Duration.ofSeconds(amount);
                case 'm' -> Duration.ofMinutes(amount);
                case 'h' -> Duration.ofHours(amount);
                case 'd' -> Duration.ofDays(amount);
                case 'w' -> Duration.ofDays(amount * 7);
                case 'M' -> Duration.ofDays(amount * 30);
                default -> Duration.ofMinutes(Long.parseLong(input));
            };
        } catch (Exception e) {
            return null;
        }
    }
}