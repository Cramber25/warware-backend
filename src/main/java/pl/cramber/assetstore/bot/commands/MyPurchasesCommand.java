package pl.cramber.assetstore.bot.commands;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
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
public class MyPurchasesCommand implements BotCommand {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("purchases", "Displays a list of your purchased assets.")
                .addOption(OptionType.INTEGER, "page", "Page number of the results", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String discordId = event.getUser().getId();
        Optional<pl.cramber.assetstore.entity.User> dbUserOpt = userRepository.findByDiscordId(discordId);

        if (dbUserOpt.isEmpty()) {
            event.reply("You must log in on our website and link your account first.").setEphemeral(true).queue();
            return;
        }

        pl.cramber.assetstore.entity.User dbUser = dbUserOpt.get();
        OptionMapping pageOption = event.getOption("page");
        int page = pageOption != null ? Math.max(1, pageOption.getAsInt()) : 1;

        List<Order> orders = orderRepository.findWithAssetByUserId(dbUser.getId()).stream()
                .filter(o -> "COMPLETED".equals(o.getStatus()))
                .collect(Collectors.toList());

        if (orders.isEmpty()) {
            EmbedBuilder emptyEmbed = new EmbedBuilder()
                    .setTitle("Your purchased assets")
                    .setColor(Color.RED)
                    .setDescription("No successful purchases found.");
            event.replyEmbeds(emptyEmbed.build()).setEphemeral(true).queue();
            return;
        }

        int pageSize = 5;
        int totalPages = (int) Math.ceil((double) orders.size() / pageSize);
        if (page > totalPages) page = totalPages;

        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, orders.size());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Your purchased assets")
                .setColor(Color.GREEN)
                .setFooter("Page " + page + " of " + totalPages + " • Use the 'page' option to navigate.");

        for (int i = startIndex; i < endIndex; i++) {
            Order order = orders.get(i);
            embed.addField(
                    order.getAsset().getTitle(),
                    "Purchase Date: <t:" + order.getCreatedAt().toEpochSecond() + ":d>\nPrice: " + order.getPurchasePrice() + " Robux",
                    false
            );
        }

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}