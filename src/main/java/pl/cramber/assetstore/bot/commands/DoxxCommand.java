package pl.cramber.assetstore.bot.commands;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.cramber.assetstore.entity.UserLoginLog;
import pl.cramber.assetstore.repository.UserLoginLogRepository;
import pl.cramber.assetstore.repository.UserRepository;

import java.awt.Color;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DoxxCommand implements BotCommand {

    private final UserRepository userRepository;
    private final UserLoginLogRepository userLoginLogRepository;

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("doxx", "Doxxes a user (Superadmin only).")
                .addOption(OptionType.USER, "user", "Select user", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        String executorDiscordId = event.getUser().getId();
        Optional<pl.cramber.assetstore.entity.User> executorOpt = userRepository.findByDiscordId(executorDiscordId);

        if (executorOpt.isEmpty() || !"SUPERADMIN".equals(executorOpt.get().getRole())) {
            event.getHook().deleteOriginal().queue();
            event.getHook().sendMessage("You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();

        EmbedBuilder startEmbed = new EmbedBuilder()
                .setTitle("System Terminal")
                .setColor(Color.YELLOW)
                .setDescription("Starting data extraction for **" + targetUser.getName() + "**...");

        event.getHook().editOriginalEmbeds(startEmbed.build()).queue(msg -> {
            int waitTime = ThreadLocalRandom.current().nextInt(3, 8);

            EmbedBuilder updatedEmbed = new EmbedBuilder()
                    .setTitle("System Terminal")
                    .setColor(Color.GREEN)
                    .setDescription("Data extraction completed for **" + targetUser.getName() + "**.");

            event.getHook().editOriginalEmbeds(updatedEmbed.build()).queueAfter(waitTime, TimeUnit.SECONDS, successMsg -> {
                Optional<pl.cramber.assetstore.entity.User> targetDbUserOpt = userRepository.findByDiscordId(targetUser.getId());

                EmbedBuilder privateEmbed = new EmbedBuilder()
                        .setTitle("Extracted Data: " + targetUser.getName())
                        .setColor(Color.RED);

                if (targetDbUserOpt.isEmpty()) {
                    privateEmbed.setDescription("This user is not registered in the database.");
                } else {
                    pl.cramber.assetstore.entity.User targetDbUser = targetDbUserOpt.get();
                    List<UserLoginLog> logs = userLoginLogRepository.findTop5ByUserIdOrderByCreatedAtDesc(targetDbUser.getId());

                    String email = targetDbUser.getEmail() != null ? targetDbUser.getEmail() : "No email linked";
                    String lastIp = logs.isEmpty() ? "No login history" : logs.get(0).getIpAddress();

                    privateEmbed.addField("Discord ID", targetDbUser.getDiscordId(), true);
                    if (targetDbUser.getRobloxId() != null) {
                        privateEmbed.addField("Roblox Username", targetDbUser.getRobloxUsername(), true);
                    }
                    privateEmbed.addField("Email", email, false);
                    privateEmbed.addField("Last Known IP", lastIp, false);
                }

                event.getHook().sendMessageEmbeds(privateEmbed.build()).setEphemeral(true).queue();
            });
        });
    }
}