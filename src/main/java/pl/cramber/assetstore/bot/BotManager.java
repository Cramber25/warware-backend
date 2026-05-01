package pl.cramber.assetstore.bot;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pl.cramber.assetstore.bot.commands.CommandManager;
import pl.cramber.assetstore.bot.events.BotEventListener;

import java.util.EnumSet;

@Service
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "true")
@RequiredArgsConstructor
public class BotManager {

    @Value("${discord.bot.token}")
    private String token;

    private final BotEventListener botEventListener;
    private final CommandManager commandManager;

    @Getter
    private JDA jda;

    @PostConstruct
    public void startBot() {
        try {
            jda = JDABuilder.createLight(token, EnumSet.of(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES))
                    .addEventListeners(botEventListener, commandManager)
                    .build();
            jda.awaitReady();

            jda.updateCommands().addCommands(commandManager.getAllCommandData()).queue();

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}