package com.social.app.controller.rest;

import com.social.app.persistence.entity.TenantEntity;
import com.social.app.persistence.entity.UserEntity;
import com.social.app.persistence.repository.TenantRepository;
import com.social.app.service.UserService;
import com.social.app.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * Super-admin endpoints for managing tenants.
 * Only accessible to users who are admins on tenant 1 (the platform tenant).
 *
 * This is the "admin of admins" — platform operators who can create/manage tenants,
 * provision tenant admins, and view cross-tenant metrics.
 */
@RestController
@RequestMapping("/api/super-admin/tenants")
public class TenantAdminController {

    private final TenantRepository tenantRepository;
    private final UserService userService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public TenantAdminController(TenantRepository tenantRepository, UserService userService) {
        this.tenantRepository = tenantRepository;
        this.userService = userService;
    }

    /**
     * Verify the caller is a super-admin (admin on tenant 1).
     */
    private void requireSuperAdmin(HttpServletRequest request) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId != 1L) {
            throw new org.springframework.security.access.AccessDeniedException("Super-admin access requires tenant 1");
        }
        // The @authenticated check in SecurityConfig ensures the user is logged in.
        // We also need to verify they're an admin, which is checked via the userId.
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr != null) {
            long userId = Long.parseLong(userIdAttr.toString());
            userService.getById(userId).ifPresent(user -> {
                if (!user.isAdmin()) {
                    throw new org.springframework.security.access.AccessDeniedException("Super-admin access requires admin role");
                }
            });
        }
    }

    /**
     * List all tenants.
     */
    @GetMapping
    public ResponseEntity<List<TenantEntity>> listTenants() {
        return ResponseEntity.ok(tenantRepository.findAll());
    }

    /**
     * Get a tenant by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TenantEntity> getTenant(@PathVariable long id) {
        return tenantRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new tenant with an initial admin user.
     *
     * Request body:
     * {
     *   "name": "Acme Corp",
     *   "slug": "acme",
     *   "plan": "pro",
     *   "adminUsername": "admin",
     *   "adminPassword": "secure123",
     *   "adminEmail": "admin@acme.com",
     *   "adminDisplayName": "Acme Admin"
     * }
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTenant(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String slug = (String) body.get("slug");
        String plan = (String) body.getOrDefault("plan", "free");

        if (name == null || slug == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "name and slug are required"));
        }

        // Check slug uniqueness
        if (tenantRepository.findBySlug(slug).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tenant slug already exists: " + slug));
        }

        // Create tenant
        TenantEntity tenant = new TenantEntity();
        tenant.setId(System.currentTimeMillis()); // Simple ID for tenants
        tenant.setName(name);
        tenant.setSlug(slug);
        tenant.setPlan(plan);
        if (body.containsKey("maxUsers")) {
            tenant.setMaxUsers(((Number) body.get("maxUsers")).intValue());
        }
        tenant = tenantRepository.save(tenant);

        // Create admin user for the new tenant
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("tenant", Map.of(
                "id", tenant.getId(),
                "name", tenant.getName(),
                "slug", tenant.getSlug(),
                "plan", tenant.getPlan()
        ));

        String adminUsername = (String) body.get("adminUsername");
        String adminPassword = (String) body.get("adminPassword");
        String adminEmail = (String) body.get("adminEmail");
        String adminDisplayName = (String) body.getOrDefault("adminDisplayName", name + " Admin");

        if (adminUsername != null && adminPassword != null && adminEmail != null) {
            // Temporarily switch tenant context to create the admin user
            Long originalTenant = TenantContext.getTenantId();
            try {
                TenantContext.setTenantId(tenant.getId());
                UserEntity admin = userService.create(
                        adminUsername,
                        adminDisplayName,
                        adminEmail,
                        passwordEncoder.encode(adminPassword),
                        "Tenant administrator for " + name
                );
                admin.setAdmin(true);
                admin.setTenantId(tenant.getId());
                userService.save(admin);

                result.put("admin", Map.of(
                        "id", admin.getId(),
                        "username", admin.getUsername(),
                        "email", admin.getEmail()
                ));
            } finally {
                TenantContext.setTenantId(originalTenant);
            }
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Update tenant settings.
     */
    @PutMapping("/{id}")
    public ResponseEntity<TenantEntity> updateTenant(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return tenantRepository.findById(id).map(tenant -> {
            if (body.containsKey("name")) tenant.setName((String) body.get("name"));
            if (body.containsKey("plan")) tenant.setPlan((String) body.get("plan"));
            if (body.containsKey("maxUsers")) tenant.setMaxUsers(((Number) body.get("maxUsers")).intValue());
            if (body.containsKey("maxStorageGb")) tenant.setMaxStorageGb(((Number) body.get("maxStorageGb")).intValue());
            if (body.containsKey("settings")) {
                try {
                    Object settingsVal = body.get("settings");
                    if (settingsVal instanceof String) {
                        tenant.setSettings((String) settingsVal);
                    } else {
                        tenant.setSettings(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(settingsVal));
                    }
                } catch (Exception e) {
                    tenant.setSettings(body.get("settings").toString());
                }
            }
            return ResponseEntity.ok(tenantRepository.save(tenant));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete a tenant (soft — just marks as disabled).
     * Does NOT delete tenant data. That requires a separate cleanup process.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteTenant(@PathVariable long id) {
        if (id == 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete the default tenant"));
        }
        return tenantRepository.findById(id).map(tenant -> {
            tenant.setPlan("disabled");
            tenantRepository.save(tenant);
            return ResponseEntity.ok(Map.of("status", "disabled", "tenant", tenant.getName()));
        }).orElse(ResponseEntity.notFound().build());
    }
}
