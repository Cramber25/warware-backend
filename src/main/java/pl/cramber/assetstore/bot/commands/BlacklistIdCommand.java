package pl.cramber.assetstore.bot.commands;

import lombok.RequiredArgsConstructor;
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
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "true")
@RequiredArgsConstructor
public class BlacklistIdCommand implements BotCommand {

    private final UserRepository userRepository;
    private final BlacklistEntryRepository blacklistEntryRepository;

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("blacklist_id", "Adds a user to the global Blacklist via Roblox ID.")
                .addOption(OptionType.STRING, "roblox_id", "Roblox User ID", true)
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
        String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString() : "No reason provided";

        if (blacklistEntryRepository.existsByRobloxIdAndIsActiveTrue(robloxId)) {
            event.reply("This Roblox ID is already in the active blacklist database.").setEphemeral(true).queue();
            return;
        }

        Optional<pl.cramber.assetstore.entity.User> dbUserOpt = userRepository.findByRobloxId(robloxId);
        String discordId = dbUserOpt.map(pl.cramber.assetstore.entity.User::getDiscordId).orElse(null);
        String robloxUsername = dbUserOpt.map(pl.cramber.assetstore.entity.User::getRobloxUsername).orElse(null);

        BlacklistEntry entry = BlacklistEntry.builder()
                .discordId(discordId != null && !discordId.startsWith("DUMMY_") ? discordId : null)
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

        if (discordId != null && !discordId.startsWith("DUMMY_")) {
            event.getGuild().ban(UserSnowflake.fromId(discordId), 0, TimeUnit.DAYS).reason(reason).queue(
                    success -> event.reply("Successfully blacklisted Roblox ID `" + robloxId + "` and banned the linked Discord account.").setEphemeral(true).queue(),
                    error -> event.reply("Successfully blacklisted Roblox ID `" + robloxId + "`, but failed to ban the linked Discord account.").setEphemeral(true).queue()
            );
        } else {
            event.reply("Successfully blacklisted Roblox ID `" + robloxId + "`. No linked Discord account found.").setEphemeral(true).queue();
        }
    }
}