package pl.cramber.assetstore.bot.commands;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.cramber.assetstore.repository.UserRepository;
import pl.cramber.assetstore.service.ModerationService;

import java.awt.Color;
import java.time.Duration;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "true")
@RequiredArgsConstructor
public class MuteCommand implements BotCommand {

    private final UserRepository userRepository;
    private final ModerationService moderationService;

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("mute", "Mutes a user for a specific duration.")
                .addOption(OptionType.USER, "user", "Select user to mute", true)
                .addOption(OptionType.STRING, "time", "Duration (e.g. 10m, 1h, 1d)", true)
                .addOption(OptionType.STRING, "reason", "Reason for muting", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String executorId = event.getUser().getId();
        Optional<pl.cramber.assetstore.entity.User> executorOpt = userRepository.findByDiscordId(executorId);

        if (executorOpt.isEmpty()) {
            event.reply("You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        String executorRole = executorOpt.get().getRole();
        if (!"SUPERADMIN".equals(executorRole) && !"ADMIN".equals(executorRole) && !"MODERATOR".equals(executorRole)) {
            event.reply("You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        Member targetMember = event.getOption("user").getAsMember();
        if (targetMember == null) {
            event.reply("User is not in the server.").setEphemeral(true).queue();
            return;
        }

        if (!event.getMember().canInteract(targetMember)) {
            event.reply("You cannot moderate this user due to Discord role hierarchy.").setEphemeral(true).queue();
            return;
        }

        String timeStr = event.getOption("time").getAsString();
        String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString() : "No reason provided";

        Duration duration = parseDuration(timeStr);
        if (duration == null) {
            event.reply("Invalid time format. Please use formats like `10m`, `1h`, `1d`.").setEphemeral(true).queue();
            return;
        }

        event.getGuild().timeoutFor(targetMember, duration).reason(reason).queue(
                success -> {
                    moderationService.logAndNotify(targetMember.getUser(), event.getUser(), "MUTE", reason, timeStr);

                    EmbedBuilder embed = new EmbedBuilder()
                            .setColor(Color.RED)
                            .setDescription("Successfully muted " + targetMember.getAsMention() + " for " + timeStr + ".");
                    event.replyEmbeds(embed.build()).queue();
                },
                error -> event.reply("Failed to mute user. Check my role hierarchy and permissions.").setEphemeral(true).queue()
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
                default -> Duration.ofMinutes(Long.parseLong(input));
            };
        } catch (Exception e) {
            return null;
        }
    }
}