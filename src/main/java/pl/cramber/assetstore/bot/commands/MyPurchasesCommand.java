package pl.cramber.assetstore.bot.commands;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import pl.cramber.assetstore.entity.Order;
import pl.cramber.assetstore.repository.OrderRepository;
import pl.cramber.assetstore.repository.UserRepository;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "true")
@RequiredArgsConstructor
public class MyPurchasesCommand implements BotCommand {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private static final int PAGE_SIZE = 5;

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("purchases", "Displays a list of your purchased assets.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.deferReply().setEphemeral(true).queue();
        handlePage(event.getUser().getId(), 0, event);
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":");
        if (parts.length != 3) return;

        String targetUserId = parts[1];
        if (!event.getUser().getId().equals(targetUserId)) {
            event.reply("You cannot interact with this menu.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        int page = Integer.parseInt(parts[2]);
        handlePage(targetUserId, page, event);
    }

    private void handlePage(String discordId, int page, Object eventContext) {
        Optional<pl.cramber.assetstore.entity.User> dbUserOpt = userRepository.findByDiscordId(discordId);

        if (dbUserOpt.isEmpty()) {
            sendError(eventContext, "You must log in on our website and link your account first.");
            return;
        }

        pl.cramber.assetstore.entity.User dbUser = dbUserOpt.get();

        Page<Order> ordersPage = orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                dbUser.getId(),
                "COMPLETED",
                PageRequest.of(page, PAGE_SIZE)
        );

        if (ordersPage.isEmpty() && page == 0) {
            EmbedBuilder emptyEmbed = new EmbedBuilder()
                    .setTitle("Your purchased assets")
                    .setColor(Color.RED)
                    .setDescription("No successful purchases found.");
            sendEmbed(eventContext, emptyEmbed, new ArrayList<>());
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Your purchased assets")
                .setColor(Color.GREEN)
                .setFooter("Page " + (page + 1) + " of " + Math.max(1, ordersPage.getTotalPages()));

        for (Order order : ordersPage.getContent()) {
            embed.addField(
                    order.getAsset().getTitle(),
                    "Purchase Date: <t:" + order.getCreatedAt().toEpochSecond() + ":d>\nPrice: " + order.getPurchasePrice() + " Robux",
                    false
            );
        }

        List<Button> buttons = new ArrayList<>();
        Button prevButton = Button.primary("purchases:" + discordId + ":" + (page - 1), "Previous");
        Button nextButton = Button.primary("purchases:" + discordId + ":" + (page + 1), "Next");

        if (page == 0) prevButton = prevButton.asDisabled();
        if (page >= ordersPage.getTotalPages() - 1) nextButton = nextButton.asDisabled();

        buttons.add(prevButton);
        buttons.add(nextButton);

        sendEmbed(eventContext, embed, buttons);
    }

    private void sendError(Object eventContext, String message) {
        if (eventContext instanceof SlashCommandInteractionEvent slashEvent) {
            slashEvent.getHook().sendMessage(message).queue();
        } else if (eventContext instanceof ButtonInteractionEvent btnEvent) {
            btnEvent.getHook().sendMessage(message).setEphemeral(true).queue();
        }
    }

    private void sendEmbed(Object eventContext, EmbedBuilder embed, List<Button> buttons) {
        if (eventContext instanceof SlashCommandInteractionEvent slashEvent) {
            if (buttons == null || buttons.isEmpty()) {
                slashEvent.getHook().sendMessageEmbeds(embed.build()).queue();
            } else {
                slashEvent.getHook().sendMessageEmbeds(embed.build()).setComponents(ActionRow.of(buttons)).queue();
            }
        } else if (eventContext instanceof ButtonInteractionEvent btnEvent) {
            if (buttons == null || buttons.isEmpty()) {
                btnEvent.getHook().editOriginalEmbeds(embed.build()).setComponents().queue();
            } else {
                btnEvent.getHook().editOriginalEmbeds(embed.build()).setComponents(ActionRow.of(buttons)).queue();
            }
        }
    }
}