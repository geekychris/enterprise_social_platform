package com.social.gateway;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes gateway metrics and health info.
 */
@RestController
public class GatewayInfoController {

    private final ConnectionRegistry registry;

    public GatewayInfoController(ConnectionRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "service", "ws-gateway",
                "engine", "Netty (non-blocking)",
                "connections", registry.getTotalConnections(),
                "uniqueUsers", registry.getTotalUsers()
        );
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
