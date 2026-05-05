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
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "true")
@RequiredArgsConstructor
public class UnmuteCommand implements BotCommand {

    private final UserRepository userRepository;
    private final ModerationService moderationService;

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("unmute", "Removes a timeout from a user.")
                .addOption(OptionType.USER, "user", "Select user to unmute", true)
                .addOption(OptionType.STRING, "reason", "Reason for unmuting", false);
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

        Member targetMember = event.getOption("user").getAsMember();
        if (targetMember == null) {
            event.getHook().deleteOriginal().queue();
            event.getHook().sendMessage("User is not in the server.").setEphemeral(true).queue();
            return;
        }

        if (!event.getMember().canInteract(targetMember)) {
            event.getHook().deleteOriginal().queue();
            event.getHook().sendMessage("You cannot moderate this user due to Discord role hierarchy.").setEphemeral(true).queue();
            return;
        }

        String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString() : "No reason provided";

        event.getGuild().removeTimeout(targetMember).reason(reason).queue(
                success -> {
                    moderationService.logWithoutDM(
                            targetMember.getId(),
                            targetMember.getUser().getName(),
                            targetMember.getAsMention(),
                            event.getUser(),
                            "UNMUTE",
                            reason,
                            null
                    );

                    EmbedBuilder embed = new EmbedBuilder()
                            .setColor(Color.GREEN)
                            .setDescription("Successfully unmuted " + targetMember.getAsMention() + ".");
                    event.getHook().editOriginalEmbeds(embed.build()).queue();
                },
                error -> {
                    event.getHook().deleteOriginal().queue();
                    event.getHook().sendMessage("Failed to unmute user. Check my role hierarchy and permissions.").setEphemeral(true).queue();
                }
        );
    }
}