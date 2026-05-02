package pl.cramber.assetstore.bot.commands;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.cramber.assetstore.repository.UserRepository;
import pl.cramber.assetstore.service.ModerationService;

import java.util.Optional;

@Component
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "true")
@RequiredArgsConstructor
public class KickCommand implements BotCommand {

    private final UserRepository userRepository;
    private final ModerationService moderationService;

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("kick", "Kicks a user from the server.")
                .addOption(OptionType.USER, "user", "Select user to kick", true)
                .addOption(OptionType.STRING, "reason", "Reason for kicking", false);
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

        String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString() : "No reason provided";

        event.getGuild().kick(targetMember).reason(reason).queue(
                success -> {
                    moderationService.logAndNotify(targetMember.getUser(), event.getUser(), "KICK", reason, null);
                    event.reply("Successfully kicked " + targetMember.getAsMention() + ".").setEphemeral(true).queue();
                },
                error -> event.reply("Failed to kick user. Check my role hierarchy and permissions.").setEphemeral(true).queue()
        );
    }
}