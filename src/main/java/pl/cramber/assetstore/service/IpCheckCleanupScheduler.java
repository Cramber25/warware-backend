package pl.cramber.assetstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.cramber.assetstore.repository.IpCheckRepository;

import java.time.LocalDateTime;

@Service
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class IpCheckCleanupScheduler {

    private final IpCheckRepository ipCheckRepository;

    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void cleanupOldIpChecks() {
        ipCheckRepository.deleteOlderThan(LocalDateTime.now().minusMonths(3));
    }
}