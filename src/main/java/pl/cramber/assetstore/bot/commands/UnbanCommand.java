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
import org.springframework.transaction.annotation.Transactional;
import pl.cramber.assetstore.repository.TempBanRepository;
import pl.cramber.assetstore.repository.UserRepository;
import pl.cramber.assetstore.service.ModerationService;

import java.awt.Color;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "true")
@RequiredArgsConstructor
public class UnbanCommand implements BotCommand {

    private final UserRepository userRepository;
    private final TempBanRepository tempBanRepository;
    private final ModerationService moderationService;

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("unban", "Unbans a user from the server by their Discord ID.")
                .addOption(OptionType.STRING, "discord_id", "Discord User ID", true)
                .addOption(OptionType.STRING, "reason", "Reason for unbanning", false);
    }

    @Override
    @Transactional
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

        String targetId = event.getOption("discord_id").getAsString();
        String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString() : "No reason provided";

        event.getGuild().unban(UserSnowflake.fromId(targetId)).reason(reason).queue(
                success -> {
                    tempBanRepository.deleteByDiscordIdAndGuildId(targetId, event.getGuild().getId());

                    moderationService.logWithoutDM(targetId, "Unknown", "<@" + targetId + ">", event.getUser(), "UNBAN", reason, null);

                    EmbedBuilder embed = new EmbedBuilder()
                            .setColor(Color.GREEN)
                            .setDescription("Successfully unbanned <@" + targetId + ">.");
                    event.replyEmbeds(embed.build()).queue();
                },
                error -> event.reply("Failed to unban user. Are you sure they are banned and the ID is correct?").setEphemeral(true).queue()
        );
    }
}