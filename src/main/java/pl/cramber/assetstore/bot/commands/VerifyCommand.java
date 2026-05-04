package pl.cramber.assetstore.bot.commands;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.cramber.assetstore.repository.UserRepository;
import pl.cramber.assetstore.service.StoreSettingService;

import java.awt.Color;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "true")
@RequiredArgsConstructor
public class VerifyCommand implements BotCommand {

    private final UserRepository userRepository;
    private final StoreSettingService storeSettingService;

    @Value("${FRONTEND_URL:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("verify", "Syncs your Roblox account and updates your roles.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String discordId = event.getUser().getId();
        Guild guild = event.getGuild();
        Member member = event.getMember();

        if (guild == null || member == null) {
            event.reply("This command can only be used within a server.").setEphemeral(true).queue();
            return;
        }

        Map<String, String> settings = storeSettingService.getAllSettings();
        String verifiedRoleId = settings.get("DISCORD_VERIFIED_ROLE_ID");
        String unverifiedRoleId = settings.get("DISCORD_UNVERIFIED_ROLE_ID");
        String verifyChannelId = settings.get("DISCORD_VERIFY_CHANNEL_ID");

        boolean isVerified = false;
        boolean hasUnverifiedRole = false;

        if (verifiedRoleId != null && !verifiedRoleId.isEmpty()) {
            Role vr = guild.getRoleById(verifiedRoleId);
            if (vr != null && member.getRoles().contains(vr)) isVerified = true;
        }

        if (unverifiedRoleId != null && !unverifiedRoleId.isEmpty()) {
            Role ur = guild.getRoleById(unverifiedRoleId);
            if (ur != null && member.getRoles().contains(ur)) hasUnverifiedRole = true;
        }

        Optional<pl.cramber.assetstore.entity.User> dbUserOpt = userRepository.findByDiscordId(discordId);

        if (dbUserOpt.isEmpty()) {
            event.reply("You are not registered in our database. Please log in on our website first to link your account.")
                    .setComponents(ActionRow.of(Button.link(frontendUrl, "Log In")))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        pl.cramber.assetstore.entity.User dbUser = dbUserOpt.get();

        if (dbUser.getRobloxId() == null) {
            event.reply("You haven't linked your Roblox account yet. Please visit your dashboard to connect it.")
                    .setComponents(ActionRow.of(Button.link(frontendUrl + "/dashboard", "Link Roblox")))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (isVerified && !hasUnverifiedRole) {
            event.reply("You are already fully verified and your roles are up to date!")
                    .setEphemeral(true)
                    .queue();
            return;
        }

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
            member.modifyNickname(newNick).queue();
        } catch (Exception ignored) {}

        if (verifyChannelId != null && !verifyChannelId.isEmpty()) {
            TextChannel channel = guild.getTextChannelById(verifyChannelId);
            if (channel != null) {
                EmbedBuilder publicEmbed = new EmbedBuilder()
                        .setTitle("Verification Successful")
                        .setColor(Color.GREEN)
                        .setDescription(member.getAsMention() + " has successfully verified their account as **" + robloxUsername + "**.");

                if (dbUser.getRobloxAvatarUrl() != null) {
                    publicEmbed.setThumbnail(dbUser.getRobloxAvatarUrl());
                }

                channel.sendMessageEmbeds(publicEmbed.build()).queue();
            }
        }

        event.reply("You have been successfully verified!").setEphemeral(true).queue();
    }
}