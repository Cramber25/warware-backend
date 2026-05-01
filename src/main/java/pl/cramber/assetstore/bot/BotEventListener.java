package pl.cramber.assetstore.bot.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.cramber.assetstore.entity.StoreSetting;
import pl.cramber.assetstore.repository.StoreSettingRepository;
import pl.cramber.assetstore.repository.UserRepository;

import java.awt.Color;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "true")
@RequiredArgsConstructor
public class BotEventListener extends ListenerAdapter {

    private final UserRepository userRepository;
    private final StoreSettingRepository storeSettingRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DiscordMessageData {
        private String content;
        private EmbedData embed;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class EmbedData {
            private String title;
            private String description;
            private String color;
            private String thumbnail;
            private String image;
            private FooterData footer;

            @Data
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class FooterData {
                private String text;
                private String iconUrl;
            }
        }
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        String discordId = event.getUser().getId();
        Guild guild = event.getGuild();

        Optional<pl.cramber.assetstore.entity.User> userOpt = userRepository.findByDiscordId(discordId);

        Map<String, String> settings = storeSettingRepository.findAll().stream()
                .collect(Collectors.toMap(StoreSetting::getSettingKey, StoreSetting::getSettingValue));

        String verifiedRoleId = settings.get("DISCORD_VERIFIED_ROLE_ID");
        String unverifiedRoleId = settings.get("DISCORD_UNVERIFIED_ROLE_ID");
        String welcomeChannelId = settings.get("DISCORD_WELCOME_CHANNEL_ID");
        String welcomeMessage = settings.get("DISCORD_WELCOME_MESSAGE");

        if (userOpt.isPresent() && userOpt.get().getRobloxId() != null) {
            String robloxUsername = userOpt.get().getRobloxUsername();

            if (verifiedRoleId != null && !verifiedRoleId.isEmpty()) {
                Role verifiedRole = guild.getRoleById(verifiedRoleId);
                if (verifiedRole != null) guild.addRoleToMember(event.getMember(), verifiedRole).queue();
            }

            String newNick = robloxUsername.length() > 32 ? robloxUsername.substring(0, 32) : robloxUsername;
            event.getMember().modifyNickname(newNick).queue();

        } else {
            if (unverifiedRoleId != null && !unverifiedRoleId.isEmpty()) {
                Role unverifiedRole = guild.getRoleById(unverifiedRoleId);
                if (unverifiedRole != null) guild.addRoleToMember(event.getMember(), unverifiedRole).queue();
            }
        }

        if (welcomeChannelId != null && !welcomeChannelId.isEmpty() && welcomeMessage != null && !welcomeMessage.isEmpty()) {
            TextChannel welcomeChannel = guild.getTextChannelById(welcomeChannelId);
            if (welcomeChannel != null) {
                sendMessage(welcomeChannel, welcomeMessage, event.getMember(), event.getUser(), guild);
            }
        }
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        Guild guild = event.getGuild();

        Map<String, String> settings = storeSettingRepository.findAll().stream()
                .collect(Collectors.toMap(StoreSetting::getSettingKey, StoreSetting::getSettingValue));

        String leaveChannelId = settings.get("DISCORD_LEAVE_CHANNEL_ID");
        String leaveMessage = settings.get("DISCORD_LEAVE_MESSAGE");

        if (leaveChannelId != null && !leaveChannelId.isEmpty() && leaveMessage != null && !leaveMessage.isEmpty()) {
            TextChannel leaveChannel = guild.getTextChannelById(leaveChannelId);
            if (leaveChannel != null) {
                sendMessage(leaveChannel, leaveMessage, event.getMember(), event.getUser(), guild);
            }
        }
    }

    private void sendMessage(TextChannel channel, String json, Member member, User user, Guild guild) {
        try {
            DiscordMessageData data = objectMapper.readValue(json, DiscordMessageData.class);
            MessageCreateBuilder builder = new MessageCreateBuilder();

            if (data.getContent() != null && !data.getContent().isEmpty()) {
                builder.setContent(replacePlaceholders(data.getContent(), member, user, guild));
            }

            if (data.getEmbed() != null) {
                EmbedBuilder embedBuilder = new EmbedBuilder();
                DiscordMessageData.EmbedData embedData = data.getEmbed();

                if (embedData.getTitle() != null) {
                    embedBuilder.setTitle(replacePlaceholders(embedData.getTitle(), member, user, guild));
                }
                if (embedData.getDescription() != null) {
                    embedBuilder.setDescription(replacePlaceholders(embedData.getDescription(), member, user, guild));
                }
                if (embedData.getColor() != null && !embedData.getColor().isEmpty()) {
                    try {
                        embedBuilder.setColor(Color.decode(embedData.getColor()));
                    } catch (Exception ignored) {}
                }
                if (embedData.getThumbnail() != null && !embedData.getThumbnail().isEmpty()) {
                    embedBuilder.setThumbnail(replacePlaceholders(embedData.getThumbnail(), member, user, guild));
                }
                if (embedData.getImage() != null && !embedData.getImage().isEmpty()) {
                    embedBuilder.setImage(replacePlaceholders(embedData.getImage(), member, user, guild));
                }
                if (embedData.getFooter() != null) {
                    String footerText = embedData.getFooter().getText() != null ? replacePlaceholders(embedData.getFooter().getText(), member, user, guild) : null;
                    String footerIcon = embedData.getFooter().getIconUrl() != null ? replacePlaceholders(embedData.getFooter().getIconUrl(), member, user, guild) : null;
                    embedBuilder.setFooter(footerText, footerIcon);
                }
                builder.setEmbeds(embedBuilder.build());
            }

            channel.sendMessage(builder.build()).queue();
        } catch (Exception e) {
            channel.sendMessage(replacePlaceholders(json, member, user, guild)).queue();
        }
    }

    private String replacePlaceholders(String text, Member member, User user, Guild guild) {
        if (text == null) return null;
        return text
                .replace("{user}", member != null ? member.getAsMention() : user.getAsMention())
                .replace("{user_name}", user.getName())
                .replace("{user_id}", user.getId())
                .replace("{user_avatar}", user.getEffectiveAvatarUrl())
                .replace("{server_name}", guild.getName())
                .replace("{member_count}", String.valueOf(guild.getMemberCount()));
    }
}