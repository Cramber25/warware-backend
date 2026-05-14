package pl.cramber.assetstore.bot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import pl.cramber.assetstore.repository.UserRepository;
import pl.cramber.assetstore.service.StoreSettingService;

import java.awt.Color;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "true")
@RequiredArgsConstructor
public class BotEventListener extends ListenerAdapter {

    private final UserRepository userRepository;
    private final StoreSettingService storeSettingService;
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
        try {
            String discordId = event.getUser().getId();
            Guild guild = event.getGuild();
            Member member = event.getMember();

            if (member == null) return;

            Optional<pl.cramber.assetstore.entity.User> userOpt = userRepository.findByDiscordId(discordId);
            Map<String, String> settings = storeSettingService.getAllSettings();

            String verifiedRoleId = settings.get("DISCORD_VERIFIED_ROLE_ID");
            String unverifiedRoleId = settings.get("DISCORD_UNVERIFIED_ROLE_ID");
            String welcomeChannelId = settings.get("DISCORD_WELCOME_CHANNEL_ID");
            String welcomeMessage = settings.get("DISCORD_WELCOME_MESSAGE");

            if (userOpt.isPresent() && userOpt.get().getRobloxId() != null) {
                String robloxUsername = userOpt.get().getRobloxUsername();

                if (verifiedRoleId != null && !verifiedRoleId.isEmpty()) {
                    Role verifiedRole = guild.getRoleById(verifiedRoleId);
                    if (verifiedRole != null) {
                        guild.addRoleToMember(member, verifiedRole).queue(null, e -> log.warn("Failed to add verified role"));
                    }
                }

                if (unverifiedRoleId != null && !unverifiedRoleId.isEmpty()) {
                    Role unverifiedRole = guild.getRoleById(unverifiedRoleId);
                    if (unverifiedRole != null && member.getRoles().contains(unverifiedRole)) {
                        guild.removeRoleFromMember(member, unverifiedRole).queue(null, e -> log.warn("Failed to remove unverified role"));
                    }
                }

                if (robloxUsername != null) {
                    String newNick = robloxUsername.length() > 32 ? robloxUsername.substring(0, 32) : robloxUsername;
                    member.modifyNickname(newNick).queue(null, e -> log.warn("Failed to modify nickname (hierarchy/perms)"));
                }
            } else {
                if (unverifiedRoleId != null && !unverifiedRoleId.isEmpty()) {
                    Role unverifiedRole = guild.getRoleById(unverifiedRoleId);
                    if (unverifiedRole != null) {
                        guild.addRoleToMember(member, unverifiedRole).queue(null, e -> log.warn("Failed to add unverified role"));
                    }
                }
            }

            if (welcomeChannelId != null && !welcomeChannelId.isEmpty() && welcomeMessage != null && !welcomeMessage.isEmpty()) {
                TextChannel welcomeChannel = guild.getTextChannelById(welcomeChannelId);
                if (welcomeChannel != null) {
                    sendMessage(welcomeChannel, welcomeMessage, member, event.getUser(), guild);
                }
            }
        } catch (Exception e) {
            log.error("Error in onGuildMemberJoin", e);
        }
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        try {
            Guild guild = event.getGuild();
            Map<String, String> settings = storeSettingService.getAllSettings();

            String leaveChannelId = settings.get("DISCORD_LEAVE_CHANNEL_ID");
            String leaveMessage = settings.get("DISCORD_LEAVE_MESSAGE");

            if (leaveChannelId != null && !leaveChannelId.isEmpty() && leaveMessage != null && !leaveMessage.isEmpty()) {
                TextChannel leaveChannel = guild.getTextChannelById(leaveChannelId);
                if (leaveChannel != null) {
                    sendMessage(leaveChannel, leaveMessage, event.getMember(), event.getUser(), guild);
                }
            }
        } catch (Exception e) {
            log.error("Error in onGuildMemberRemove", e);
        }
    }

    private void sendMessage(TextChannel channel, String json, Member member, User user, Guild guild) {
        try {
            try {
                json = URLDecoder.decode(json, StandardCharsets.UTF_8.name());
            } catch (Exception ignored) {}

            if (json.trim().startsWith("{")) {
                DiscordMessageData data = objectMapper.readValue(json, DiscordMessageData.class);
                MessageCreateBuilder builder = new MessageCreateBuilder();

                if (data.getContent() != null && !data.getContent().isEmpty()) {
                    builder.setContent(replacePlaceholders(data.getContent(), member, user, guild));
                }

                if (data.getEmbed() != null) {
                    EmbedBuilder embedBuilder = new EmbedBuilder();
                    DiscordMessageData.EmbedData embedData = data.getEmbed();

                    if (embedData.getTitle() != null) embedBuilder.setTitle(replacePlaceholders(embedData.getTitle(), member, user, guild));
                    if (embedData.getDescription() != null) embedBuilder.setDescription(replacePlaceholders(embedData.getDescription(), member, user, guild));

                    if (embedData.getColor() != null && !embedData.getColor().isEmpty()) {
                        try {
                            embedBuilder.setColor(Color.decode(embedData.getColor().startsWith("#") ? embedData.getColor() : "#" + embedData.getColor()));
                        } catch (Exception ignored) {}
                    }

                    if (embedData.getThumbnail() != null && !embedData.getThumbnail().isEmpty()) embedBuilder.setThumbnail(replacePlaceholders(embedData.getThumbnail(), member, user, guild));
                    if (embedData.getImage() != null && !embedData.getImage().isEmpty()) embedBuilder.setImage(replacePlaceholders(embedData.getImage(), member, user, guild));

                    if (embedData.getFooter() != null) {
                        String footerText = embedData.getFooter().getText() != null ? replacePlaceholders(embedData.getFooter().getText(), member, user, guild) : null;
                        String footerIcon = embedData.getFooter().getIconUrl() != null ? replacePlaceholders(embedData.getFooter().getIconUrl(), member, user, guild) : null;
                        if (footerText != null) embedBuilder.setFooter(footerText, footerIcon);
                    }

                    if (!embedBuilder.isEmpty()) builder.setEmbeds(embedBuilder.build());
                }

                if (!builder.isEmpty()) channel.sendMessage(builder.build()).queue(null, e -> log.error("Failed to send JSON message"));
            } else {
                channel.sendMessage(replacePlaceholders(json, member, user, guild)).queue(null, e -> log.error("Failed to send raw message"));
            }
        } catch (Exception e) {
            String fallback = replacePlaceholders(json, member, user, guild);
            if (fallback != null && !fallback.isEmpty()) {
                channel.sendMessage(fallback).queue(null, err -> log.error("Failed to send fallback message"));
            }
        }
    }

    private String replacePlaceholders(String text, Member member, User user, Guild guild) {
        if (text == null || user == null || guild == null) return text;
        return text
                .replace("{user}", member != null ? member.getAsMention() : user.getAsMention())
                .replace("{user_name}", user.getName() != null ? user.getName() : "Unknown")
                .replace("{user_id}", user.getId())
                .replace("{user_avatar}", user.getEffectiveAvatarUrl())
                .replace("{server_name}", guild.getName() != null ? guild.getName() : "Unknown Server")
                .replace("{member_count}", String.valueOf(guild.getMemberCount()));
    }
}