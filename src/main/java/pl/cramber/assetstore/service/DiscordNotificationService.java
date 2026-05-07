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

    @Value("${FRONTEND_URL:http://localhost:5173}")
    private String frontendUrl;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public void sendDepositNotification(String robloxUsername, String discordId, Integer amount) {
        String content = String.format("**%s** (`%s`) deposited **%dR$**", robloxUsername, discordId, amount);
        sendMessage(content);
    }

    public void sendPurchaseNotification(String robloxUsername, String discordId, String assetName, String assetId) {
        String content = String.format("**%s** (`%s`) bought asset **%s** (`%s`)", robloxUsername, discordId, assetName, assetId);
        sendMessage(content);
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