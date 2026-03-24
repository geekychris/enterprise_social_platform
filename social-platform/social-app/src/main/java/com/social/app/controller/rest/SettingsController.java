package com.social.app.controller.rest;

import com.social.app.persistence.entity.PlatformSettingEntity;
import com.social.app.persistence.repository.PlatformSettingRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class SettingsController {

    private final PlatformSettingRepository settingsRepository;

    public SettingsController(PlatformSettingRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @GetMapping("/api/settings/theme")
    public ResponseEntity<Map<String, String>> getTheme() {
        var setting = settingsRepository.findById("theme").orElse(null);
        String theme = setting != null ? setting.getValue() : "modern-collaboration";
        return ResponseEntity.ok(Map.of("theme", theme));
    }

    @PutMapping("/api/admin/settings/theme")
    public ResponseEntity<Map<String, String>> setTheme(@RequestBody Map<String, String> body) {
        String theme = body.get("theme");
        if (theme == null || theme.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "theme is required"));
        }

        var setting = settingsRepository.findById("theme").orElse(new PlatformSettingEntity());
        setting.setKey("theme");
        setting.setValue(theme);
        settingsRepository.save(setting);

        return ResponseEntity.ok(Map.of("theme", theme));
    }
}
