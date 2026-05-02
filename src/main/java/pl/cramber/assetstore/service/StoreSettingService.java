package pl.cramber.assetstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import pl.cramber.assetstore.entity.StoreSetting;
import pl.cramber.assetstore.repository.StoreSettingRepository;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoreSettingService {

    private final StoreSettingRepository storeSettingRepository;

    @Cacheable("storeSettings")
    public Map<String, String> getAllSettings() {
        return storeSettingRepository.findAll().stream()
                .collect(Collectors.toMap(StoreSetting::getSettingKey, StoreSetting::getSettingValue, (a, b) -> a));
    }

    @CacheEvict(value = "storeSettings", allEntries = true)
    public void evictSettingsCache() {
    }
}