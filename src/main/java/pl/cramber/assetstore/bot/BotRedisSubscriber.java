package pl.cramber.assetstore.bot;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import pl.cramber.assetstore.entity.StoreSetting;
import pl.cramber.assetstore.repository.StoreSettingRepository;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "true")
@RequiredArgsConstructor
public class BotRedisSubscriber implements MessageListener {

    private final BotManager botManager;
    private final StoreSettingRepository storeSettingRepository;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        String[] parts = body.split("::");

        if (parts.length != 2) return;

        String discordId = parts[0];
        String robloxUsername = parts[1];

        Map<String, String> settings = storeSettingRepository.findAll().stream()
                .collect(Collectors.toMap(StoreSetting::getSettingKey, StoreSetting::getSettingValue));

        String guildId = settings.get("DISCORD_GUILD_ID");
        if (guildId == null || guildId.isEmpty()) return;

        Guild guild = botManager.getJda().getGuildById(guildId);
        if (guild == null) return;

        String verifiedRoleId = settings.get("DISCORD_VERIFIED_ROLE_ID");
        String unverifiedRoleId = settings.get("DISCORD_UNVERIFIED_ROLE_ID");

        guild.retrieveMemberById(discordId).queue(member -> {
            if (verifiedRoleId != null && !verifiedRoleId.isEmpty()) {
                Role verifiedRole = guild.getRoleById(verifiedRoleId);
                if (verifiedRole != null) guild.addRoleToMember(member, verifiedRole).queue();
            }

            if (unverifiedRoleId != null && !unverifiedRoleId.isEmpty()) {
                Role unverifiedRole = guild.getRoleById(unverifiedRoleId);
                if (unverifiedRole != null) guild.removeRoleFromMember(member, unverifiedRole).queue();
            }

            String newNick = robloxUsername.length() > 32 ? robloxUsername.substring(0, 32) : robloxUsername;
            member.modifyNickname(newNick).queue();
        }, failure -> {});
    }
}