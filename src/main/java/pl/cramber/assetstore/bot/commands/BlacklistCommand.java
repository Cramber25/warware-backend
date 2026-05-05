package pl.cramber.assetstore.bot.commands;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
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
public class BlacklistCommand implements BotCommand {

    private final UserRepository userRepository;
    private final BlacklistEntryRepository blacklistEntryRepository;
    private final ModerationService moderationService;

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("blacklist", "Bans a user on Discord and adds them to the global Blacklist.")
                .addOption(OptionType.USER, "user", "Select user to blacklist", true)
                .addOption(OptionType.STRING, "reason", "Reason for blacklisting", false);
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
        if (!"SUPERADMIN".equals(executorRole)) {
            event.getHook().deleteOriginal().queue();
            event.getHook().sendMessage("You do not have permission to use this command. Superadmin only.").setEphemeral(true).queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();
        Member targetMember = event.getOption("user").getAsMember();

        if (targetMember != null && !event.getMember().canInteract(targetMember)) {
            event.getHook().deleteOriginal().queue();
            event.getHook().sendMessage("You cannot moderate this user due to Discord role hierarchy.").setEphemeral(true).queue();
            return;
        }

        String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString() : "No reason provided";

        if (blacklistEntryRepository.findByDiscordIdAndIsActiveTrue(targetUser.getId()).isPresent()) {
            event.getHook().deleteOriginal().queue();
            event.getHook().sendMessage("User is already in the active blacklist database.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder dmEmbed = new EmbedBuilder()
                .setTitle("Moderation Notice")
                .setColor(Color.RED)
                .addField("Action", "BLACKLIST (BAN)", true)
                .addField("Reason", reason, false)
                .addField("Duration", "PERMANENT", true);

        Runnable executeBanAndLog = () -> {
            event.getGuild().ban(targetUser, 0, TimeUnit.DAYS).reason(reason).queue(
                    success -> {
                        saveAndLog(event, targetUser, executorId, reason);
                        EmbedBuilder publicReply = new EmbedBuilder()
                                .setColor(Color.RED)
                                .setDescription("Successfully blacklisted and banned " + targetUser.getAsMention() + ".");
                        event.getHook().editOriginalEmbeds(publicReply.build()).queue();
                    },
                    error -> {
                        saveAndLog(event, targetUser, executorId, reason);
                        EmbedBuilder publicReply = new EmbedBuilder()
                                .setColor(Color.RED)
                                .setDescription("Successfully blacklisted and banned " + targetUser.getAsMention() + ".");
                        event.getHook().editOriginalEmbeds(publicReply.build()).queue();
                    }
            );
        };

        targetUser.openPrivateChannel().queue(
                ch -> ch.sendMessageEmbeds(dmEmbed.build()).queue(s -> executeBanAndLog.run(), e -> executeBanAndLog.run()),
                e -> executeBanAndLog.run()
        );
    }

    private void saveAndLog(SlashCommandInteractionEvent event, User targetUser, String executorId, String reason) {
        Optional<pl.cramber.assetstore.entity.User> dbUserOpt = userRepository.findByDiscordId(targetUser.getId());
        String robloxId = null;
        String robloxUsername = null;

        if (dbUserOpt.isPresent()) {
            pl.cramber.assetstore.entity.User dbUser = dbUserOpt.get();
            robloxId = dbUser.getRobloxId();
            robloxUsername = dbUser.getRobloxUsername();
            dbUser.setBanned(true);
            userRepository.save(dbUser);
        }

        BlacklistEntry entry = BlacklistEntry.builder()
                .discordId(targetUser.getId())
                .robloxId(robloxId)
                .robloxUsername(robloxUsername)
                .reason(reason)
                .adminDiscordId(executorId)
                .isActive(true)
                .build();
        blacklistEntryRepository.save(entry);

        moderationService.logWithoutDM(targetUser.getId(), targetUser.getName(), targetUser.getAsMention(), event.getUser(), "BLACKLIST (BAN)", reason, "PERMANENT");
    }
}