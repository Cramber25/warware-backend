package pl.cramber.assetstore.bot.commands;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
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
public class CheckUserCommand implements BotCommand {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private static final int PAGE_SIZE = 5;

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("check-user", "Displays user's balance, assets, and Roblox link (Superadmin only).")
                .addOption(OptionType.USER, "user", "Select user", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String executorDiscordId = event.getUser().getId();
        Optional<pl.cramber.assetstore.entity.User> executorOpt = userRepository.findByDiscordId(executorDiscordId);

        if (executorOpt.isEmpty() || !"SUPERADMIN".equals(executorOpt.get().getRole())) {
            event.reply("You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();
        handlePage(executorDiscordId, targetUser.getId(), targetUser.getName(), 0, event);
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":");
        if (parts.length != 5) return;

        String requiredAdminId = parts[1];
        String targetUserId = parts[2];
        String targetUserName = parts[3];
        int page = Integer.parseInt(parts[4]);

        if (!event.getUser().getId().equals(requiredAdminId)) {
            event.reply("You cannot interact with this menu.").setEphemeral(true).queue();
            return;
        }

        handlePage(requiredAdminId, targetUserId, targetUserName, page, event);
    }

    private void handlePage(String adminId, String targetDiscordId, String targetDiscordName, int page, Object eventContext) {
        Optional<pl.cramber.assetstore.entity.User> targetDbUserOpt = userRepository.findByDiscordId(targetDiscordId);

        if (targetDbUserOpt.isEmpty()) {
            sendError(eventContext, "This user has never logged in on the website.");
            return;
        }

        pl.cramber.assetstore.entity.User targetDbUser = targetDbUserOpt.get();

        Page<Order> ordersPage = orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                targetDbUser.getId(),
                "COMPLETED",
                PageRequest.of(page, PAGE_SIZE)
        );

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Profile: " + targetDiscordName)
                .setColor(targetDbUser.isBanned() ? Color.RED : Color.GREEN);

        if (targetDbUser.getRobloxId() != null) {
            embed.addField("Roblox Account", "✅ Linked\n**Username:** " + targetDbUser.getRobloxUsername() + "\n**ID:** " + targetDbUser.getRobloxId(), true);
            if (targetDbUser.getRobloxAvatarUrl() != null && !targetDbUser.getRobloxAvatarUrl().isEmpty()) {
                embed.setThumbnail(targetDbUser.getRobloxAvatarUrl());
            }
        } else {
            embed.addField("Roblox Account", "❌ Not Linked", true);
        }

        embed.addField("Store Status", targetDbUser.isBanned() ? "⛔ Banned" : "🟢 Active", true);
        embed.addField("Balance", "🪙 " + targetDbUser.getBalance() + " Robux", false);

        if (ordersPage.isEmpty() && page == 0) {
            embed.setDescription("**Purchased assets:**\nNo successful purchases found.");
            sendEmbed(eventContext, embed, new ArrayList<>());
            return;
        }

        embed.setFooter("Page " + (page + 1) + " of " + Math.max(1, ordersPage.getTotalPages()));

        for (Order order : ordersPage.getContent()) {
            embed.addField(
                    order.getAsset().getTitle(),
                    "Purchase Date: <t:" + order.getCreatedAt().toEpochSecond() + ":d>\nPrice: " + order.getPurchasePrice() + " Robux",
                    false
            );
        }

        List<Button> buttons = new ArrayList<>();
        String baseId = "check-user:" + adminId + ":" + targetDiscordId + ":" + targetDiscordName;

        Button prevButton = Button.primary(baseId + ":" + (page - 1), "Previous");
        Button nextButton = Button.primary(baseId + ":" + (page + 1), "Next");

        if (page == 0) prevButton = prevButton.asDisabled();
        if (page >= ordersPage.getTotalPages() - 1) nextButton = nextButton.asDisabled();

        buttons.add(prevButton);
        buttons.add(nextButton);

        sendEmbed(eventContext, embed, buttons);
    }

    private void sendError(Object eventContext, String message) {
        if (eventContext instanceof SlashCommandInteractionEvent slashEvent) {
            slashEvent.reply(message).setEphemeral(true).queue();
        } else if (eventContext instanceof ButtonInteractionEvent btnEvent) {
            btnEvent.reply(message).setEphemeral(true).queue();
        }
    }

    private void sendEmbed(Object eventContext, EmbedBuilder embed, List<Button> buttons) {
        if (eventContext instanceof SlashCommandInteractionEvent slashEvent) {
            if (buttons == null || buttons.isEmpty()) {
                slashEvent.replyEmbeds(embed.build()).setEphemeral(false).queue();
            } else {
                slashEvent.replyEmbeds(embed.build()).setComponents(ActionRow.of(buttons)).setEphemeral(false).queue();
            }
        } else if (eventContext instanceof ButtonInteractionEvent btnEvent) {
            if (buttons == null || buttons.isEmpty()) {
                btnEvent.editMessageEmbeds(embed.build()).setComponents().queue();
            } else {
                btnEvent.editMessageEmbeds(embed.build()).setComponents(ActionRow.of(buttons)).queue();
            }
        }
    }
}