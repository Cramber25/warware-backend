package pl.cramber.assetstore.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class DiscordNotificationService {

    @Value("${DISCORD_TOKEN:}")
    private String botToken;

    @Value("${DISCORD_CHANNEL_ID:}")
    private String channelId;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public void sendDepositNotification(String robloxUsername, String discordId, Integer amount) {
        String content = String.format("**%s** (`%s`) deposited **%dR$**", robloxUsername, discordId, amount);
        sendMessage(content);
    }

    public void sendPurchaseNotification(String robloxUsername, String discordId, String assetName, String assetId) {
        String content = String.format("**%s** (`%s`) bought asset **%s** (`%s`)", robloxUsername, discordId, assetName, assetId);
        sendMessage(content);
    }

    private void sendMessage(String content) {
        if (botToken == null || channelId == null || botToken.isBlank() || channelId.isBlank()) {
            return;
        }

        try {
            String payload = "{\"content\": \"" + content.replace("\"", "\\\"") + "\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://discord.com/api/v10/channels/" + channelId + "/messages"))
                    .header("Authorization", "Bot " + botToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
        }
    }
}