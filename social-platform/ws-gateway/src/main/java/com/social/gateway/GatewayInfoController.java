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
    private final int maxConnections;

    public GatewayInfoController(ConnectionRegistry registry,
                                  @org.springframework.beans.factory.annotation.Value("${gateway.max-connections:100000}") int maxConnections) {
        this.registry = registry;
        this.maxConnections = maxConnections;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        int current = registry.getTotalConnections();
        return Map.of(
                "service", "ws-gateway",
                "engine", "Netty (non-blocking)",
                "connections", current,
                "maxConnections", maxConnections,
                "utilization", String.format("%.1f%%", (current * 100.0) / maxConnections),
                "uniqueUsers", registry.getTotalUsers(),
                "eventLoopThreads", Runtime.getRuntime().availableProcessors()
        );
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
