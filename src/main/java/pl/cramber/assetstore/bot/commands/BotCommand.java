package pl.cramber.assetstore.bot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public interface BotCommand {
    SlashCommandData getCommandData();
    void execute(SlashCommandInteractionEvent event);
    default void onButtonInteraction(ButtonInteractionEvent event) {}
}