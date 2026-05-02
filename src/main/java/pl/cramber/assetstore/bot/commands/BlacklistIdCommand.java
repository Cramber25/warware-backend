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
import pl.cramber.assetstore.entity.BlacklistEntry;
import pl.cramber.assetstore.repository.BlacklistEntryRepository;
import pl.cramber.assetstore.repository.UserRepository;
import pl.cramber.assetstore.service.ModerationService;

import java.awt.Color;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "true")
@RequiredArgsConstructor
public class BlacklistIdCommand implements BotCommand {

    private final UserRepository userRepository;
    private final BlacklistEntryRepository blacklistEntryRepository;
    private final ModerationService moderationService;

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("blacklist_id", "Adds a user to the global Blacklist via IDs.")
                .addOption(OptionType.STRING, "roblox_id", "Roblox User ID", true)
                .addOption(OptionType.STRING, "discord_id", "Discord User ID", false)
                .addOption(OptionType.STRING, "reason", "Reason for blacklisting", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String executorId = event.getUser().getId();
        Optional<pl.cramber.assetstore.entity.User> executorOpt = userRepository.findByDiscordId(executorId);

        if (executorOpt.isEmpty() || !"SUPERADMIN".equals(executorOpt.get().getRole())) {
            event.reply("You do not have permission to use this command. Superadmin only.").setEphemeral(true).queue();
            return;
        }

        String robloxId = event.getOption("roblox_id").getAsString();
        String providedDiscordId = event.getOption("discord_id") != null ? event.getOption("discord_id").getAsString() : null;
        String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString() : "No reason provided";

        if (blacklistEntryRepository.existsByRobloxIdAndIsActiveTrue(robloxId)) {
            event.reply("This Roblox ID is already in the active blacklist database.").setEphemeral(true).queue();
            return;
        }

        Optional<pl.cramber.assetstore.entity.User> dbUserOpt = userRepository.findByRobloxId(robloxId);

        String discordId = providedDiscordId != null ? providedDiscordId : dbUserOpt.map(pl.cramber.assetstore.entity.User::getDiscordId).orElse(null);
        String robloxUsername = dbUserOpt.map(pl.cramber.assetstore.entity.User::getRobloxUsername).orElse(null);

        String finalDiscordId = (discordId != null && !discordId.startsWith("DUMMY_")) ? discordId : null;

        EmbedBuilder replyEmbed = new EmbedBuilder().setColor(Color.RED);
        if (finalDiscordId != null) {
            replyEmbed.setDescription("Successfully blacklisted Roblox ID `" + robloxId + "` and banned Discord ID `<@" + finalDiscordId + ">`.");
        } else {
            replyEmbed.setDescription("Successfully blacklisted Roblox ID `" + robloxId + "`. No linked Discord account found.");
        }
        event.replyEmbeds(replyEmbed.build()).queue();

        Runnable executeBanAndLog = () -> {
            if (finalDiscordId != null) {
                event.getGuild().ban(UserSnowflake.fromId(finalDiscordId), 0, TimeUnit.DAYS).reason(reason).queue(
                        success -> saveAndLog(event, finalDiscordId, robloxId, robloxUsername, executorId, reason, dbUserOpt),
                        error -> saveAndLog(event, finalDiscordId, robloxId, robloxUsername, executorId, reason, dbUserOpt)
                );
            } else {
                saveAndLog(event, null, robloxId, robloxUsername, executorId, reason, dbUserOpt);
            }
        };

        if (finalDiscordId != null) {
            event.getJDA().retrieveUserById(finalDiscordId).queue(
                    user -> {
                        EmbedBuilder dmEmbed = new EmbedBuilder()
                                .setTitle("Moderation Notice")
                                .setColor(Color.RED)
                                .addField("Action", "BLACKLIST (BAN)", true)
                                .addField("Reason", reason, false)
                                .addField("Duration", "PERMANENT", true);

                        user.openPrivateChannel().queue(
                                ch -> ch.sendMessageEmbeds(dmEmbed.build()).queue(s -> executeBanAndLog.run(), e -> executeBanAndLog.run()),
                                e -> executeBanAndLog.run()
                        );
                    },
                    e -> executeBanAndLog.run()
            );
        } else {
            executeBanAndLog.run();
        }
    }

    private void saveAndLog(SlashCommandInteractionEvent event, String finalDiscordId, String robloxId, String robloxUsername, String executorId, String reason, Optional<pl.cramber.assetstore.entity.User> dbUserOpt) {
        BlacklistEntry entry = BlacklistEntry.builder()
                .discordId(finalDiscordId)
                .robloxId(robloxId)
                .robloxUsername(robloxUsername)
                .reason(reason)
                .adminDiscordId(executorId)
                .isActive(true)
                .build();
        blacklistEntryRepository.save(entry);

        if (dbUserOpt.isPresent()) {
            pl.cramber.assetstore.entity.User dbUser = dbUserOpt.get();
            dbUser.setBanned(true);
            userRepository.save(dbUser);
        }

        String targetId = finalDiscordId != null ? finalDiscordId : robloxId;
        String targetName = robloxUsername != null ? robloxUsername : "Unknown";
        String targetMention = finalDiscordId != null ? "<@" + finalDiscordId + ">" : "Roblox ID: " + robloxId;

        moderationService.logWithoutDM(targetId, targetName, targetMention, event.getUser(), "BLACKLIST (BAN)", reason, "PERMANENT");
    }
}