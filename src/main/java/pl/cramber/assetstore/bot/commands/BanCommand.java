package pl.cramber.assetstore.bot.commands;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
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
import pl.cramber.assetstore.util.TimeUtils;

import java.awt.Color;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "true")
@RequiredArgsConstructor
public class BanCommand implements BotCommand {

    private final UserRepository userRepository;
    private final TempBanRepository tempBanRepository;
    private final ModerationService moderationService;

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("ban", "Bans a user from the server.")
                .addOption(OptionType.USER, "user", "Select user to ban", true)
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

        User targetUser = event.getOption("user").getAsUser();
        Member targetMember = event.getOption("user").getAsMember();

        if (targetMember != null && !event.getMember().canInteract(targetMember)) {
            event.getHook().deleteOriginal().queue();
            event.getHook().sendMessage("You cannot moderate this user due to Discord role hierarchy.").setEphemeral(true).queue();
            return;
        }

        String targetRole = userRepository.findByDiscordId(targetUser.getId())
                .map(pl.cramber.assetstore.entity.User::getRole)
                .orElse("USER");

        if (!moderationService.canModerate(executorRole, targetRole)) {
            event.getHook().deleteOriginal().queue();
            event.getHook().sendMessage("You cannot moderate this user due to database role hierarchy.").setEphemeral(true).queue();
            return;
        }

        String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString() : "No reason provided";
        String timeStr = event.getOption("time") != null ? event.getOption("time").getAsString() : null;

        Duration duration = TimeUtils.parseDuration(timeStr);
        if (timeStr != null && duration == null) {
            event.getHook().deleteOriginal().queue();
            event.getHook().sendMessage("Invalid time format. Please use formats like `1h`, `1d`, `1w`.").setEphemeral(true).queue();
            return;
        }

        ZonedDateTime unbanAt = duration != null ? ZonedDateTime.now().plus(duration) : null;
        String durationDisplay = timeStr != null ? timeStr : "PERMANENT";

        EmbedBuilder dmEmbed = new EmbedBuilder()
                .setTitle("Moderation Notice")
                .setColor(Color.RED)
                .addField("Action", "BAN", true)
                .addField("Reason", reason, false)
                .addField("Duration", durationDisplay, true);

        Runnable executeBanAndLog = () -> {
            event.getGuild().ban(targetUser, 0, TimeUnit.DAYS).reason(reason).queue(
                    success -> {
                        if (unbanAt != null) {
                            tempBanRepository.save(TempBan.builder()
                                    .discordId(targetUser.getId())
                                    .guildId(event.getGuild().getId())
                                    .unbanAt(unbanAt)
                                    .build());
                        }

                        moderationService.logWithoutDM(targetUser.getId(), targetUser.getName(), targetUser.getAsMention(), event.getUser(), "BAN", reason, durationDisplay);

                        EmbedBuilder embed = new EmbedBuilder()
                                .setColor(Color.RED)
                                .setTitle("BAN")
                                .setDescription("Successfully banned " + targetUser.getAsMention() + ".")
                                .addField("Reason", reason, false)
                                .addField("Duration", durationDisplay, true);

                        event.getHook().sendMessageEmbeds(embed.build()).queue();
                    },
                    error -> {
                        event.getHook().deleteOriginal().queue();
                        event.getHook().sendMessage("Failed to ban user. Check hierarchy or IDs.").setEphemeral(true).queue();
                    }
            );
        };

        targetUser.openPrivateChannel().queue(
                ch -> ch.sendMessageEmbeds(dmEmbed.build()).queue(s -> executeBanAndLog.run(), e -> executeBanAndLog.run()),
                e -> executeBanAndLog.run()
        );
    }
}