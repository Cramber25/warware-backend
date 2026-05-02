package pl.cramber.assetstore.bot.commands;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.User;
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

import java.util.Optional;

@Component
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "true")
@RequiredArgsConstructor
public class RemoveBlacklistCommand implements BotCommand {

    private final UserRepository userRepository;
    private final BlacklistEntryRepository blacklistEntryRepository;

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("remove_blacklist", "Removes a user from the active blacklist but keeps the history.")
                .addOption(OptionType.USER, "user", "Select user", false)
                .addOption(OptionType.STRING, "roblox_id", "Or provide Roblox ID", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String executorId = event.getUser().getId();
        Optional<pl.cramber.assetstore.entity.User> executorOpt = userRepository.findByDiscordId(executorId);

        if (executorOpt.isEmpty() || !"SUPERADMIN".equals(executorOpt.get().getRole())) {
            event.reply("You do not have permission to use this command. Superadmin only.").setEphemeral(true).queue();
            return;
        }

        User targetUser = event.getOption("user") != null ? event.getOption("user").getAsUser() : null;
        String robloxId = event.getOption("roblox_id") != null ? event.getOption("roblox_id").getAsString() : null;

        if (targetUser == null && robloxId == null) {
            event.reply("You must provide either a Discord user or a Roblox ID.").setEphemeral(true).queue();
            return;
        }

        Optional<BlacklistEntry> entryOpt = Optional.empty();

        if (targetUser != null) {
            entryOpt = blacklistEntryRepository.findByDiscordIdAndIsActiveTrue(targetUser.getId());
        } else if (robloxId != null) {
            entryOpt = blacklistEntryRepository.findByRobloxIdAndIsActiveTrue(robloxId);
        }

        if (entryOpt.isEmpty()) {
            event.reply("No active blacklist entry found for the provided details.").setEphemeral(true).queue();
            return;
        }

        BlacklistEntry entry = entryOpt.get();
        entry.setActive(false);
        blacklistEntryRepository.save(entry);

        if (entry.getDiscordId() != null && !entry.getDiscordId().isEmpty()) {
            userRepository.findByDiscordId(entry.getDiscordId()).ifPresent(u -> {
                u.setBanned(false);
                userRepository.save(u);
            });
            event.getGuild().unban(UserSnowflake.fromId(entry.getDiscordId())).queue(null, err -> {});
        }

        if (entry.getRobloxId() != null && !entry.getRobloxId().isEmpty()) {
            userRepository.findByRobloxId(entry.getRobloxId()).ifPresent(u -> {
                u.setBanned(false);
                userRepository.save(u);
            });
        }

        event.reply("Successfully removed the active blacklist. The entry has been archived for history.").setEphemeral(true).queue();
    }
}