package pl.cramber.assetstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class DiscordNotificationService {

    private final StoreSettingService storeSettingService;

    @Value("${DISCORD_TOKEN:}")
    private String botToken;

    @Value("${DISCORD_CHANNEL_ID:}")
    private String channelId;

    @Value("${DISCORD_GUILD_ID:}")
    private String guildId;

    @Value("${FRONTEND_URL:http://localhost:5173}")
    private String frontendUrl;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public void sendDepositNotification(String robloxUsername, String discordId, Integer amount) {
        String content = String.format("**%s** (`%s`) deposited **%dR$**", robloxUsername, discordId, amount);
        sendMessage(channelId, content);
    }

    public void sendPurchaseNotification(String robloxUsername, String discordId, String assetName, String assetId) {
        String content = String.format("**%s** (`%s`) bought asset **%s** (`%s`)", robloxUsername, discordId, assetName, assetId);
        sendMessage(channelId, content);
    }

    public void sendAppealNotification(String robloxUsername, String discordId, String type, String appealContent) {
        Map<String, String> settings = storeSettingService.getAllSettings();
        String appealChannelId = settings.get("DISCORD_APPEAL_CHANNEL_ID");

        if (botToken == null || appealChannelId == null || botToken.isBlank() || appealChannelId.isBlank()) return;

        try {
            String safeContent = appealContent
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\r", "")
                    .replace("\n", "\\n");

            int embedColor = "BAN".equalsIgnoreCase(type) ? 16711680 : 16753920;

            String payload = "{"
                    + "\"embeds\": [{"
                    + "\"title\": \"New " + type + " Appeal\","
                    + "\"color\": " + embedColor + ","
                    + "\"fields\": ["
                    + "{\"name\": \"User\", \"value\": \"" + robloxUsername + " (`" + discordId + "`)\", \"inline\": false},"
                    + "{\"name\": \"Content\", \"value\": \"" + safeContent + "\", \"inline\": false}"
                    + "]"
                    + "}]"
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://discord.com/api/v10/channels/" + appealChannelId + "/messages"))
                    .header("Authorization", "Bot " + botToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
        }
    }

    public void revokeDiscordBan(String discordId) {
        if (botToken == null || guildId == null || botToken.isBlank() || guildId.isBlank()) {
            System.err.println("Discord token or guild ID is missing, cannot revoke ban.");
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://discord.com/api/v10/guilds/" + guildId + "/bans/" + discordId))
                    .header("Authorization", "Bot " + botToken)
                    .header("X-Audit-Log-Reason", "Appeal Accepted")
                    .DELETE()
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() != 204) {
                            System.err.println("Failed to revoke ban for user " + discordId + ". Discord returned status: " + response.statusCode());
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendOrderCompleteDM(String discordId, String assetName) {
        if (botToken == null || botToken.isBlank()) return;

        try {
            String payload = "{\"recipient_id\": \"" + discordId + "\"}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://discord.com/api/v10/users/@me/channels"))
                    .header("Authorization", "Bot " + botToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200 || response.statusCode() == 201) {
                            try {
                                String dmChannelId = response.body().split("\"id\":\\s*\"")[1].split("\"")[0];

                                String content = String.format("Your order for **%s** has been fulfilled!\\nYou can download your file in the client panel: %s/dashboard", assetName, frontendUrl);
                                String msgPayload = "{\"content\": \"" + content + "\"}";

                                HttpRequest msgRequest = HttpRequest.newBuilder()
                                        .uri(URI.create("https://discord.com/api/v10/channels/" + dmChannelId + "/messages"))
                                        .header("Authorization", "Bot " + botToken)
                                        .header("Content-Type", "application/json")
                                        .POST(HttpRequest.BodyPublishers.ofString(msgPayload))
                                        .build();

                                httpClient.sendAsync(msgRequest, HttpResponse.BodyHandlers.discarding());
                            } catch (Exception ignored) {
                            }
                        }
                    });
        } catch (Exception ignored) {}
    }

    private void sendMessage(String targetChannelId, String content) {
        if (botToken == null || targetChannelId == null || botToken.isBlank() || targetChannelId.isBlank()) {
            return;
        }

        try {
            String payload = "{\"content\": \"" + content.replace("\"", "\\\"") + "\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://discord.com/api/v10/channels/" + targetChannelId + "/messages"))
                    .header("Authorization", "Bot " + botToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
        }
    }
}