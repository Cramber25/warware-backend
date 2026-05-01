package pl.cramber.assetstore.bot.commands;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.cramber.assetstore.entity.StoreSetting;
import pl.cramber.assetstore.repository.StoreSettingRepository;
import pl.cramber.assetstore.repository.UserRepository;

import java.awt.Color;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "true")
@RequiredArgsConstructor
public class VerifyCommand implements BotCommand {

    private final UserRepository userRepository;
    private final StoreSettingRepository storeSettingRepository;

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("verify", "Syncs your Roblox account and updates your roles.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String discordId = event.getUser().getId();
        Optional<pl.cramber.assetstore.entity.User> dbUserOpt = userRepository.findByDiscordId(discordId);

        if (dbUserOpt.isEmpty() || dbUserOpt.get().getRobloxId() == null) {
            event.reply("You haven't linked your Roblox account yet. Please visit our website to verify.")
                    .setEphemeral(false)
                    .queue();
            return;
        }

        pl.cramber.assetstore.entity.User dbUser = dbUserOpt.get();
        Guild guild = event.getGuild();
        Member member = event.getMember();

        if (guild == null || member == null) {
            event.reply("This command can only be used within a server.").setEphemeral(false).queue();
            return;
        }

        Map<String, String> settings = storeSettingRepository.findAll().stream()
                .collect(Collectors.toMap(StoreSetting::getSettingKey, StoreSetting::getSettingValue));

        String verifiedRoleId = settings.get("DISCORD_VERIFIED_ROLE_ID");
        String unverifiedRoleId = settings.get("DISCORD_UNVERIFIED_ROLE_ID");

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Verification Sync")
                .setColor(Color.GREEN)
                .setThumbnail(dbUser.getRobloxAvatarUrl())
                .setDescription("Successfully synced your account with **" + dbUser.getRobloxUsername() + "**.");

        if (verifiedRoleId != null && !verifiedRoleId.isEmpty()) {
            Role verifiedRole = guild.getRoleById(verifiedRoleId);
            if (verifiedRole != null) {
                guild.addRoleToMember(member, verifiedRole).queue();
            }
        }

        if (unverifiedRoleId != null && !unverifiedRoleId.isEmpty()) {
            Role unverifiedRole = guild.getRoleById(unverifiedRoleId);
            if (unverifiedRole != null) {
                guild.removeRoleFromMember(member, unverifiedRole).queue();
            }
        }

        String robloxUsername = dbUser.getRobloxUsername();
        String newNick = robloxUsername.length() > 32 ? robloxUsername.substring(0, 32) : robloxUsername;
        try {
            member.modifyNickname(newNick).queue(
                    null,
                    error -> embed.appendDescription("\n\n*Note: Could not change your nickname due to hierarchy or missing permissions.*")
            );
        } catch (Exception ignored) {}

        event.replyEmbeds(embed.build()).setEphemeral(false).queue();
    }
}