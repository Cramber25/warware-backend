package pl.cramber.assetstore.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.cramber.assetstore.service.VpnDetectionService;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class VpnBlockerFilter extends OncePerRequestFilter {

    private final VpnDetectionService vpnDetectionService;

    @Value("${FRONTEND_URL:http://localhost:5173}")
    private String frontendUrl;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (path.startsWith("/api/auth/roblox") || path.startsWith("/oauth2") || path.startsWith("/login")) {

            String ipAddress = request.getHeader("CF-Connecting-IP");
            if (ipAddress == null || ipAddress.isEmpty()) {
                ipAddress = request.getRemoteAddr();
            }
            if (ipAddress != null && ipAddress.contains(",")) {
                ipAddress = ipAddress.split(",")[0].trim();
            }

            if (vpnDetectionService.isVpnOrProxy(ipAddress)) {
                response.sendRedirect(frontendUrl + "?error=vpn_proxy_not_allowed");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}