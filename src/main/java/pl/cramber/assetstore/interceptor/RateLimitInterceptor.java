package pl.cramber.assetstore.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String ipAddress = request.getHeader("CF-Connecting-IP");
        if (ipAddress == null || ipAddress.isEmpty()) {
            ipAddress = request.getRemoteAddr();
        }

        String key = "ratelimit:" + (request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : ipAddress);

        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, 1, TimeUnit.SECONDS);
        }

        if (count != null && count > 10L) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            return false;
        }

        return true;
    }
}