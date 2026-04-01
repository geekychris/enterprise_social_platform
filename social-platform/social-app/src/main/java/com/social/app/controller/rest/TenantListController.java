package com.social.app.controller.rest;

import com.social.app.persistence.repository.TenantRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Public endpoint for the login page tenant selector.
 * Returns only non-sensitive info: id, name, slug, plan.
 */
@RestController
@RequestMapping("/api/tenants")
public class TenantListController {

    private final TenantRepository tenantRepository;

    public TenantListController(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> listTenants() {
        return ResponseEntity.ok(tenantRepository.findAll().stream()
                .filter(t -> !"disabled".equals(t.getPlan()))
                .map(t -> Map.<String, Object>of(
                        "id", t.getId(),
                        "name", t.getName(),
                        "slug", t.getSlug(),
                        "plan", t.getPlan()
                ))
                .toList());
    }
}
