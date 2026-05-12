package pl.cramber.assetstore.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import pl.cramber.assetstore.dto.VpnApiResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class VpnDetectionService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${vpnapi.key}")
    private String apiKey;

    @Cacheable(value = "ipSecurityCheck", key = "#ipAddress")
    public boolean isVpnOrProxy(String ipAddress) {
        if ("127.0.0.1".equals(ipAddress) || "0:0:0:0:0:0:0:1".equals(ipAddress)) {
            return false;
        }

        try {
            String url = String.format("https://vpnapi.io/api/%s?key=%s", ipAddress, apiKey);
            VpnApiResponse response = restTemplate.getForObject(url, VpnApiResponse.class);

            if (response != null && response.getSecurity() != null) {
                return response.getSecurity().isSuspicious();
            }
        } catch (Exception e) {
            log.error("Error while connecting to vpnapi.io for IP: {}", ipAddress, e);
            return false;
        }

        return false;
    }
}