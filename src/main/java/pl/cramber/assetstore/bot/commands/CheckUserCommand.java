package pl.cramber.assetstore.bot.commands;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.cramber.assetstore.entity.Order;
import pl.cramber.assetstore.repository.OrderRepository;
import pl.cramber.assetstore.repository.UserRepository;

import java.awt.Color;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "true")
@RequiredArgsConstructor
public class CheckUserCommand implements BotCommand {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("check-user", "Displays user's balance, assets, and Roblox link (Superadmin only).")
                .addOption(OptionType.USER, "user", "Select user", true)
                .addOption(OptionType.INTEGER, "page", "Page number of the results", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String executorDiscordId = event.getUser().getId();
        Optional<pl.cramber.assetstore.entity.User> executorOpt = userRepository.findByDiscordId(executorDiscordId);

        if (executorOpt.isEmpty() || !"SUPERADMIN".equals(executorOpt.get().getRole())) {
            event.reply("You do not have permission to use this command.").setEphemeral(false).queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();
        Optional<pl.cramber.assetstore.entity.User> targetDbUserOpt = userRepository.findByDiscordId(targetUser.getId());

        if (targetDbUserOpt.isEmpty()) {
            event.reply("This user has never logged in on the website.").setEphemeral(false).queue();
            return;
        }

        pl.cramber.assetstore.entity.User targetDbUser = targetDbUserOpt.get();
        OptionMapping pageOption = event.getOption("page");
        int page = pageOption != null ? Math.max(1, pageOption.getAsInt()) : 1;

        List<Order> orders = orderRepository.findWithAssetByUserId(targetDbUser.getId()).stream()
                .filter(o -> "COMPLETED".equals(o.getStatus()))
                .collect(Collectors.toList());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Profile: " + targetUser.getName())
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

        if (orders.isEmpty()) {
            embed.setDescription("**Purchased assets:**\nNo successful purchases found.");
            event.replyEmbeds(embed.build()).setEphemeral(false).queue();
            return;
        }

        int pageSize = 5;
        int totalPages = (int) Math.ceil((double) orders.size() / pageSize);
        if (page > totalPages) page = totalPages;

        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, orders.size());

        embed.setDescription("**Purchased assets:**");
        embed.setFooter("Page " + page + " of " + totalPages + " • Use the 'page' option to navigate.");

        for (int i = startIndex; i < endIndex; i++) {
            Order order = orders.get(i);
            embed.addField(
                    order.getAsset().getTitle(),
                    "Purchase Date: <t:" + order.getCreatedAt().toEpochSecond() + ":d>\nPrice: " + order.getPurchasePrice() + " Robux",
                    false
            );
        }

        event.replyEmbeds(embed.build()).setEphemeral(false).queue();
    }
}