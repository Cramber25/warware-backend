package pl.cramber.assetstore.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pl.cramber.assetstore.interceptor.RateLimitInterceptor;
import pl.cramber.assetstore.interceptor.VpnBlockerInterceptor;

@Configuration
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final VpnBlockerInterceptor vpnBlockerInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(vpnBlockerInterceptor).addPathPatterns(
            "/api/auth/login",
            "/api/auth/verify"
        );
    }
}