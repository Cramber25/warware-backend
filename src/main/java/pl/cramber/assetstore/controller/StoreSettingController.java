package pl.cramber.assetstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pl.cramber.assetstore.entity.AuditLog;
import pl.cramber.assetstore.entity.StoreSetting;
import pl.cramber.assetstore.repository.AuditLogRepository;
import pl.cramber.assetstore.repository.StoreSettingRepository;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discord.bot.enabled", havingValue = "false", matchIfMissing = true)
public class StoreSettingController {

    private final StoreSettingRepository storeSettingRepository;
    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public ResponseEntity<List<StoreSetting>> getAllSettings() {
        return ResponseEntity.ok(storeSettingRepository.findAll());
    }

    @GetMapping("/{key}")
    public ResponseEntity<StoreSetting> getSetting(@PathVariable String key) {
        return storeSettingRepository.findById(key)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{key}")
    @Transactional
    public ResponseEntity<?> updateSetting(
            @PathVariable String key,
            @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal OAuth2User principal) {

        String authorities = principal.getAuthorities().toString();
        if (!authorities.contains("ROLE_SUPERADMIN")) {
            return ResponseEntity.status(403).body("Access Denied");
        }

        String value = payload.get("value");
        StoreSetting setting = storeSettingRepository.findById(key)
                .orElse(StoreSetting.builder().settingKey(key).build());

        setting.setSettingValue(value);
        storeSettingRepository.save(setting);

        auditLogRepository.save(AuditLog.builder()
                .adminDiscordId(principal.getAttribute("id"))
                .action("UPDATE_SETTING")
                .details("Updated site setting: " + key)
                .build());

        return ResponseEntity.ok(setting);
    }
}