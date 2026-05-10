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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "true")
@RequiredArgsConstructor
public class RemoveBlacklistCommand implements BotCommand {

    private final UserRepository userRepository;
    private final BlacklistEntryRepository blacklistEntryRepository;
    private final ModerationService moderationService;

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("remove_blacklist", "Removes a user from the active blacklist but keeps the history.")
                .addOption(OptionType.STRING, "discord_id", "Discord User ID", false)
                .addOption(OptionType.STRING, "roblox_id", "Or provide Roblox ID", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        String executorId = event.getUser().getId();
        Optional<pl.cramber.assetstore.entity.User> executorOpt = userRepository.findByDiscordId(executorId);

        if (executorOpt.isEmpty() || !"SUPERADMIN".equals(executorOpt.get().getRole())) {
            event.getHook().deleteOriginal().queue();
            event.getHook().sendMessage("You do not have permission to use this command. Superadmin only.").setEphemeral(true).queue();
            return;
        }

        String discordId = event.getOption("discord_id") != null ? event.getOption("discord_id").getAsString() : null;
        String robloxId = event.getOption("roblox_id") != null ? event.getOption("roblox_id").getAsString() : null;

        if (discordId == null && robloxId == null) {
            event.getHook().deleteOriginal().queue();
            event.getHook().sendMessage("You must provide either a Discord ID or a Roblox ID.").setEphemeral(true).queue();
            return;
        }

        List<BlacklistEntry> activeEntries = new ArrayList<>();

        if (discordId != null) {
            activeEntries = blacklistEntryRepository.findAllByDiscordIdAndIsActiveTrue(discordId);
        } else if (robloxId != null) {
            activeEntries = blacklistEntryRepository.findAllByRobloxIdAndIsActiveTrue(robloxId);
        }

        if (activeEntries.isEmpty()) {
            event.getHook().deleteOriginal().queue();
            event.getHook().sendMessage("No active blacklist entries found for the provided details.").setEphemeral(true).queue();
            return;
        }

        String targetDiscordId = null;
        String targetRobloxId = null;
        String targetName = "Unknown";

        for (BlacklistEntry entry : activeEntries) {
            entry.setActive(false);
            blacklistEntryRepository.save(entry);

            if (entry.getDiscordId() != null && !entry.getDiscordId().isEmpty()) targetDiscordId = entry.getDiscordId();
            if (entry.getRobloxId() != null && !entry.getRobloxId().isEmpty()) targetRobloxId = entry.getRobloxId();
            if (entry.getRobloxUsername() != null) targetName = entry.getRobloxUsername();
        }

        if (targetDiscordId != null) {
            userRepository.findByDiscordId(targetDiscordId).ifPresent(u -> {
                u.setBanned(false);
                userRepository.save(u);
            });
            event.getGuild().unban(UserSnowflake.fromId(targetDiscordId)).queue(null, err -> {});
        }

        if (targetRobloxId != null) {
            userRepository.findByRobloxId(targetRobloxId).ifPresent(u -> {
                u.setBanned(false);
                userRepository.save(u);
            });
        }

        String finalTargetId = targetDiscordId != null ? targetDiscordId : targetRobloxId;
        String targetMention = targetDiscordId != null && !targetDiscordId.startsWith("DUMMY_") ? "<@" + targetDiscordId + ">" : "Roblox ID: " + targetRobloxId;

        moderationService.logWithoutDM(finalTargetId, targetName, targetMention, event.getUser(), "REMOVE BLACKLIST (UNBAN)", "Blacklist revoked and active entries cleared.", null);

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.GREEN)
                .setDescription("Successfully removed the active blacklist. All entries have been archived for history.");
        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }
}