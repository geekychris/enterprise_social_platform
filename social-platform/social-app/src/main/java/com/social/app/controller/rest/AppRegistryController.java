package com.social.app.controller.rest;

import com.social.app.persistence.entity.AppEntity;
import com.social.app.persistence.entity.AppInstallationEntity;
import com.social.app.service.AppService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/app-registry")
public class AppRegistryController {

    private final AppService appService;

    public AppRegistryController(AppService appService) {
        this.appService = appService;
    }

    @GetMapping("/apps")
    public ResponseEntity<List<Map<String, Object>>> listApps(@RequestParam(required = false) String type) {
        List<AppEntity> apps = appService.listApps(type);
        List<Map<String, Object>> result = apps.stream().map(this::toAppMap).toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/apps")
    public ResponseEntity<?> registerApp(@RequestBody Map<String, Object> body, Authentication auth) {
        long userId = (Long) auth.getPrincipal();

        String name = (String) body.get("name");
        String slug = (String) body.get("slug");
        String description = (String) body.get("description");
        String webhookUrl = (String) body.get("webhookUrl");
        String appType = (String) body.get("appType");

        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) body.get("permissions");

        Map<String, Object> result = appService.registerApp(name, slug, description, webhookUrl, appType, permissions, userId);
        AppEntity app = (AppEntity) result.get("app");
        String apiKey = (String) result.get("apiKey");

        Map<String, Object> response = new java.util.HashMap<>(toAppMap(app));
        response.put("apiKey", apiKey);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/apps/{id}")
    public ResponseEntity<?> getApp(@PathVariable long id) {
        return appService.getById(id)
                .map(app -> ResponseEntity.ok(toAppMap(app)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/install")
    public ResponseEntity<?> installApp(@RequestBody Map<String, Object> body,
                                         @RequestHeader(value = "X-Debug-User-Id", required = false) String debugUserId,
                                         Authentication auth) {
        long userId;
        if (debugUserId != null) {
            userId = Long.parseLong(debugUserId);
        } else if (auth != null && auth.getPrincipal() instanceof Long) {
            userId = (Long) auth.getPrincipal();
        } else {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        long appId = ((Number) body.get("appId")).longValue();
        String installType = (String) body.get("installType");
        long targetId = ((Number) body.get("targetId")).longValue();

        AppInstallationEntity installation = appService.installApp(appId, installType, targetId, userId);

        return ResponseEntity.ok(Map.of(
                "id", installation.getId(),
                "appId", installation.getAppId(),
                "installType", installation.getInstallType(),
                "targetId", installation.getTargetId(),
                "active", installation.getActive(),
                "createdAt", installation.getCreatedAt().toString()
        ));
    }

    /**
     * Returns apps installed by the current user (for support page access control).
     */
    @GetMapping("/my-installations")
    public ResponseEntity<?> getMyInstallations(
            @RequestHeader(value = "X-Debug-User-Id", required = false) String debugUserId,
            Authentication auth) {
        long userId;
        if (debugUserId != null) {
            userId = Long.parseLong(debugUserId);
        } else if (auth != null && auth.getPrincipal() instanceof Long) {
            userId = (Long) auth.getPrincipal();
        } else {
            return ResponseEntity.ok(List.of());
        }

        List<AppInstallationEntity> installations = appService.getInstallationsByUser(userId);
        List<Map<String, Object>> result = installations.stream().map(inst -> {
            String appName = appService.getById(inst.getAppId()).map(a -> a.getName()).orElse("Unknown");
            return Map.<String, Object>of(
                    "id", inst.getId(),
                    "appId", inst.getAppId(),
                    "appName", appName,
                    "installType", inst.getInstallType(),
                    "targetId", inst.getTargetId()
            );
        }).toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/installations")
    public ResponseEntity<?> getInstallations(@RequestParam String type, @RequestParam long targetId) {
        List<AppInstallationEntity> installations = appService.getInstallations(type, targetId);
        List<Map<String, Object>> result = installations.stream().map(inst -> Map.<String, Object>of(
                "id", inst.getId(),
                "appId", inst.getAppId(),
                "installType", inst.getInstallType(),
                "targetId", inst.getTargetId(),
                "active", inst.getActive(),
                "createdAt", inst.getCreatedAt().toString()
        )).toList();

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/installations/{id}")
    public ResponseEntity<?> uninstallApp(@PathVariable long id) {
        appService.uninstall(id);
        return ResponseEntity.ok(Map.of("status", "uninstalled"));
    }

    @PostMapping("/activate")
    public ResponseEntity<?> activateUserApp(@RequestBody Map<String, Object> body, Authentication auth) {
        long userId = (Long) auth.getPrincipal();

        long appId = ((Number) body.get("appId")).longValue();

        AppInstallationEntity installation = appService.installApp(appId, "USER", userId, userId);

        return ResponseEntity.ok(Map.of(
                "id", installation.getId(),
                "appId", installation.getAppId(),
                "installType", installation.getInstallType(),
                "targetId", installation.getTargetId(),
                "active", installation.getActive(),
                "createdAt", installation.getCreatedAt().toString()
        ));
    }

    private Map<String, Object> toAppMap(AppEntity app) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", app.getId());
        map.put("name", app.getName());
        map.put("slug", app.getSlug());
        map.put("description", app.getDescription());
        map.put("iconUrl", app.getIconUrl());
        map.put("webhookUrl", app.getWebhookUrl());
        map.put("appType", app.getAppType());
        map.put("permissions", app.getPermissions());
        map.put("active", app.getActive());
        map.put("createdAt", app.getCreatedAt() != null ? app.getCreatedAt().toString() : null);
        return map;
    }
}
