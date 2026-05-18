package pl.cramber.assetstore.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import pl.cramber.assetstore.dto.VpnApiResponse;
import pl.cramber.assetstore.entity.IpCheck;
import pl.cramber.assetstore.repository.IpCheckRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class VpnDetectionService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final IpCheckRepository ipCheckRepository;

    @Value("${vpnapi.key}")
    private String apiKey;

    public boolean isVpnOrProxy(String ipAddress) {
        if ("127.0.0.1".equals(ipAddress) || "0:0:0:0:0:0:0:1".equals(ipAddress)) {
            return false;
        }

        Optional<IpCheck> existingCheck = ipCheckRepository.findById(ipAddress);
        if (existingCheck.isPresent()) {
            IpCheck check = existingCheck.get();
            if (check.getCreatedAt().isAfter(LocalDateTime.now().minusMonths(3))) {
                return check.isProxy();
            }
            ipCheckRepository.delete(check);
        }

        try {
            String url = String.format("https://vpnapi.io/api/%s?key=%s", ipAddress, apiKey);
            VpnApiResponse response = restTemplate.getForObject(url, VpnApiResponse.class);

            if (response != null && response.getSecurity() != null) {
                boolean isSuspicious = response.getSecurity().isSuspicious();
                ipCheckRepository.save(new IpCheck(ipAddress, isSuspicious, LocalDateTime.now()));
                return isSuspicious;
            }
        } catch (Exception e) {
            log.error("VpnApi error for IP: {}", ipAddress, e);
        }

        return false;
    }
}