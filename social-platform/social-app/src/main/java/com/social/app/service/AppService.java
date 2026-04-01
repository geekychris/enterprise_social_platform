package com.social.app.service;

import com.social.app.persistence.entity.AppEntity;
import com.social.app.persistence.entity.AppEventEntity;
import com.social.app.persistence.entity.AppInstallationEntity;
import com.social.app.persistence.repository.AppEventRepository;
import com.social.app.persistence.repository.AppInstallationRepository;
import com.social.app.persistence.repository.AppRepository;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class AppService {

    private static final Logger log = LoggerFactory.getLogger(AppService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AppRepository appRepository;
    private final AppInstallationRepository installationRepository;
    private final AppEventRepository eventRepository;
    private final GlobalIdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final AppEventDeliveryService deliveryService;

    public AppService(AppRepository appRepository,
                      AppInstallationRepository installationRepository,
                      AppEventRepository eventRepository,
                      GlobalIdGenerator idGenerator,
                      ObjectMapper objectMapper,
                      @org.springframework.context.annotation.Lazy AppEventDeliveryService deliveryService) {
        this.appRepository = appRepository;
        this.installationRepository = installationRepository;
        this.eventRepository = eventRepository;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
        this.deliveryService = deliveryService;
    }

    /**
     * Register a new app. Returns the entity with the raw API key set in a transient way
     * (the hash is persisted, but the caller needs the raw key to return to the registrant).
     */
    @Transactional
    public Map<String, Object> registerApp(String name, String slug, String description,
                                            String webhookUrl, String appType,
                                            List<String> permissions, Long createdBy) {
        String rawApiKey = generateApiKey();
        String apiKeyHash = sha256(rawApiKey);

        AppEntity entity = new AppEntity();
        entity.setId(idGenerator.next(ObjectType.APP).value());
        entity.setTenantId(1L);
        entity.setName(name);
        entity.setSlug(slug);
        entity.setDescription(description);
        entity.setWebhookUrl(webhookUrl);
        entity.setAppType(appType != null ? appType : "PAGE");
        entity.setApiKeyHash(apiKeyHash);
        entity.setCreatedBy(createdBy);

        if (permissions != null && !permissions.isEmpty()) {
            try {
                entity.setPermissions(objectMapper.writeValueAsString(permissions));
            } catch (Exception e) {
                entity.setPermissions("[]");
            }
        }

        AppEntity saved = appRepository.save(entity);

        return Map.of(
                "app", saved,
                "apiKey", rawApiKey
        );
    }

    @Transactional
    public AppInstallationEntity installApp(long appId, String installType, long targetId, long installedBy) {
        // Check if already installed
        Optional<AppInstallationEntity> existing = installationRepository
                .findByAppIdAndInstallTypeAndTargetId(appId, installType, targetId);
        if (existing.isPresent()) {
            AppInstallationEntity inst = existing.get();
            if (inst.getActive()) {
                return inst; // already active
            }
            // Reactivate
            inst.setActive(true);
            return installationRepository.save(inst);
        }

        AppInstallationEntity entity = new AppInstallationEntity();
        entity.setId(idGenerator.next(ObjectType.APP_INSTALLATION).value());
        entity.setAppId(appId);
        entity.setTenantId(1L);
        entity.setInstallType(installType);
        entity.setTargetId(targetId);
        entity.setInstalledBy(installedBy);

        return installationRepository.save(entity);
    }

    @Transactional
    public void uninstall(long installationId) {
        installationRepository.findById(installationId).ifPresent(inst -> {
            inst.setActive(false);
            installationRepository.save(inst);
        });
    }

    /**
     * Publish an event to all active installations matching the given target.
     * For example, when a post is created on a page, find all PAGE installations for that pageId.
     */
    @Transactional
    public void publishEvent(String eventType, String installType, long targetId, Map<String, Object> payload) {
        // Normalize: PAGE_FEED → PAGE, GROUP_FEED → GROUP
        String normalizedType = installType;
        if (installType != null && installType.endsWith("_FEED")) {
            normalizedType = installType.replace("_FEED", "");
        }

        // Find target-specific installations (PAGE/GROUP)
        List<AppInstallationEntity> installations = new java.util.ArrayList<>(
                installationRepository.findByInstallTypeAndTargetIdAndActive(normalizedType, targetId, true));

        // Also find ORG-wide installations (match all events in tenant)
        Long tenantId = com.social.app.tenant.TenantContext.getTenantId();
        if (tenantId != null) {
            installations.addAll(
                    installationRepository.findByInstallTypeAndTargetIdAndActive("ORG", tenantId, true));
        }

        // Also find USER-level installations for the post author
        Object authorId = payload.get("authorId");
        if (authorId != null) {
            try {
                long uid = authorId instanceof Number ? ((Number) authorId).longValue() : Long.parseLong(authorId.toString());
                installations.addAll(
                        installationRepository.findByInstallTypeAndTargetIdAndActive("USER", uid, true));
            } catch (Exception ignored) {}
        }

        // Deduplicate by app ID (don't send same event twice to same app)
        java.util.Set<Long> seenAppIds = new java.util.HashSet<>();
        for (AppInstallationEntity installation : installations) {
            if (!seenAppIds.add(installation.getAppId())) continue;
            try {
                AppEventEntity event = new AppEventEntity();
                event.setId(idGenerator.next(ObjectType.APP_EVENT).value());
                event.setAppId(installation.getAppId());
                event.setInstallationId(installation.getId());
                event.setTenantId(installation.getTenantId());
                event.setEventType(eventType);
                event.setPayload(objectMapper.writeValueAsString(payload));

                eventRepository.save(event);
                // Immediately notify the delivery thread — no polling delay
                deliveryService.notifyNewEvent(installation.getAppId());
            } catch (Exception e) {
                log.warn("Failed to queue app event for installation {}: {}", installation.getId(), e.getMessage());
            }
        }
    }

    public boolean validateApiKey(long appId, String apiKey) {
        Optional<AppEntity> app = appRepository.findById(appId);
        if (app.isEmpty()) return false;
        String hash = sha256(apiKey);
        return hash.equals(app.get().getApiKeyHash());
    }

    public Optional<AppEntity> getById(long id) {
        return appRepository.findById(id);
    }

    public Optional<AppEntity> getBySlug(String slug) {
        return appRepository.findBySlugAndTenantId(slug, 1L);
    }

    public List<AppEntity> listApps(String type) {
        if (type != null && !type.isEmpty()) {
            return appRepository.findByAppTypeAndActive(type, true);
        }
        return appRepository.findByActive(true);
    }

    public List<AppInstallationEntity> getInstallations(String type, long targetId) {
        return installationRepository.findByInstallTypeAndTargetIdAndActive(type, targetId, true);
    }

    public List<AppInstallationEntity> getInstallationsByUser(long userId) {
        return installationRepository.findByInstalledBy(userId);
    }

    public List<AppEventEntity> getEvents(long appId, String status) {
        return eventRepository.findByAppIdAndStatus(appId, status);
    }

    @Transactional
    public void ackEvent(long eventId) {
        eventRepository.findById(eventId).ifPresent(event -> {
            event.setStatus("DELIVERED");
            event.setDeliveredAt(java.time.Instant.now());
            eventRepository.save(event);
        });
    }

    private String generateApiKey() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return "app_" + HexFormat.of().formatHex(bytes);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
