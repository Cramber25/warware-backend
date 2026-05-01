package pl.cramber.assetstore.bot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "true")
public class CommandManager extends ListenerAdapter {

    private final Map<String, BotCommand> commands = new HashMap<>();

    public CommandManager(List<BotCommand> commandList) {
        for (BotCommand command : commandList) {
            commands.put(command.getCommandData().getName(), command);
        }
    }

    public List<SlashCommandData> getAllCommandData() {
        return commands.values().stream()
                .map(BotCommand::getCommandData)
                .collect(Collectors.toList());
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        BotCommand command = commands.get(event.getName());
        if (command != null) {
            command.execute(event);
        }
    }
}