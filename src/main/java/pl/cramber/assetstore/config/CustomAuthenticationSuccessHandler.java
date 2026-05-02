package pl.cramber.assetstore.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import pl.cramber.assetstore.entity.UserLoginLog;
import pl.cramber.assetstore.repository.UserLoginLogRepository;
import pl.cramber.assetstore.repository.UserRepository;

import java.io.IOException;

@Component
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class CustomAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final UserLoginLogRepository userLoginLogRepository;

    @Value("${FRONTEND_URL:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        setDefaultTargetUrl(frontendUrl);

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String discordId = oAuth2User.getAttribute("id");

        userRepository.findByDiscordId(discordId).ifPresent(user -> {
            String ipAddress = request.getHeader("CF-Connecting-IP");

            if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getRemoteAddr();
            }

            if (ipAddress != null && ipAddress.contains(",")) {
                ipAddress = ipAddress.split(",")[0].trim();
            }

            UserLoginLog log = UserLoginLog.builder()
                    .user(user)
                    .ipAddress(ipAddress)
                    .build();
            userLoginLogRepository.save(log);
        });

        super.onAuthenticationSuccess(request, response, authentication);
    }
}