package com.social.app.controller.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.social.app.persistence.repository.TenantRepository;
import com.social.app.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public endpoint that returns the current tenant's branding (name, color, logo).
 * Called by the frontend on load to customize the UI per tenant.
 * No authentication required — branding is public info.
 */
@RestController
@RequestMapping("/api/branding")
public class TenantBrandingController {

    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    public TenantBrandingController(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
        this.objectMapper = new ObjectMapper();
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getBranding() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) tenantId = 1L;

        return tenantRepository.findById(tenantId).map(tenant -> {
            // Parse branding from settings JSON
            String companyName = tenant.getName();
            String primaryColor = "#3B82F6"; // default blue
            String logoUrl = null;

            try {
                if (tenant.getSettings() != null && !tenant.getSettings().equals("{}")) {
                    var settings = objectMapper.readTree(tenant.getSettings());
                    var branding = settings.get("branding");
                    if (branding != null) {
                        if (branding.has("companyName")) companyName = branding.get("companyName").asText();
                        if (branding.has("primaryColor")) primaryColor = branding.get("primaryColor").asText();
                        if (branding.has("logoUrl") && !branding.get("logoUrl").isNull()) logoUrl = branding.get("logoUrl").asText();
                    }
                }
            } catch (Exception ignored) {}

            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("tenantId", tenant.getId());
            result.put("companyName", companyName);
            result.put("slug", tenant.getSlug());
            result.put("plan", tenant.getPlan());
            result.put("primaryColor", primaryColor);
            result.put("logoUrl", logoUrl != null ? logoUrl : "");
            return ResponseEntity.ok(result);
        }).orElse(ResponseEntity.ok(Map.of(
                "tenantId", (Object) tenantId,
                "companyName", (Object) "WorkSphere",
                "slug", (Object) "default",
                "plan", "free",
                "primaryColor", "#3B82F6",
                "logoUrl", ""
        )));
    }
}
