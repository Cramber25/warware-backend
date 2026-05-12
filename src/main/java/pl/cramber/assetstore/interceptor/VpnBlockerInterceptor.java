package pl.cramber.assetstore.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import pl.cramber.assetstore.service.VpnDetectionService;

@Component
@RequiredArgsConstructor
public class VpnBlockerInterceptor implements HandlerInterceptor {

    private final VpnDetectionService vpnDetectionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String ipAddress = request.getHeader("CF-Connecting-IP");
        if (ipAddress == null || ipAddress.isEmpty()) {
            ipAddress = request.getRemoteAddr();
        }

        if (vpnDetectionService.isVpnOrProxy(ipAddress)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\": \"Accessing our service via a VPN/Proxy is not allowed.\"}");

            return false;
        }

        return true;
    }
}