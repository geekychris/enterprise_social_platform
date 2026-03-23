package com.social.app.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Client for the AOEE social graph cache, communicating via the Spring Boot proxy REST API.
 * Provides graceful degradation when AOEE is unavailable.
 *
 * AOEE proxy response formats:
 *   GET /api/edges/{src}/{edgeType}  -> {"src":..., "edgeType":..., "neighbors":[...]}
 *   GET /api/edges/{src}/{edgeType}/contains/{dst} -> {"exists": true/false}
 *   GET /api/edges/{src}/{edgeType}/count -> {"count": N}
 */
@Component
public class AoeeGraphClient {

    private static final Logger log = LoggerFactory.getLogger(AoeeGraphClient.class);

    private final RestClient restClient;

    public AoeeGraphClient(@Value("${social.aoee.host}") String host,
                           @Value("${social.aoee.proxy-port:8082}") int proxyPort) {
        this.restClient = RestClient.builder()
                .baseUrl("http://" + host + ":" + proxyPort)
                .build();
        log.info("AOEE client configured to connect to http://{}:{}", host, proxyPort);
    }

    public void addEdge(long src, String edgeType, long dst) {
        try {
            restClient.post()
                    .uri("/api/edges")
                    .body(Map.of("src", src, "edgeType", edgeType, "dst", dst))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("AOEE: failed to add edge ({}) --[{}]--> ({}): {}",
                    src, edgeType, dst, e.getMessage());
        }
    }

    public void addEdgeWithMetadata(long src, String edgeType, long dst, int metadata) {
        try {
            restClient.post()
                    .uri("/api/edges")
                    .body(Map.of("src", src, "edgeType", edgeType, "dst", dst, "metadata", metadata))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("AOEE: failed to add edge ({}) --[{}]--> ({}) meta={}: {}",
                    src, edgeType, dst, metadata, e.getMessage());
        }
    }

    public void removeEdge(long src, String edgeType, long dst) {
        try {
            restClient.delete()
                    .uri("/api/edges?src={src}&edgeType={edgeType}&dst={dst}", src, edgeType, dst)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("AOEE: failed to remove edge ({}) --[{}]--> ({}): {}",
                    src, edgeType, dst, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public List<Long> getNeighbors(long src, String edgeType) {
        try {
            // AOEE proxy returns: {"src":..., "edgeType":..., "neighbors":[...]}
            var result = restClient.get()
                    .uri("/api/edges/{src}/{edgeType}", src, edgeType)
                    .retrieve()
                    .body(Map.class);
            if (result == null) return Collections.emptyList();
            Object neighbors = result.get("neighbors");
            if (neighbors == null) return Collections.emptyList();
            return ((List<Number>) neighbors).stream()
                    .map(Number::longValue)
                    .toList();
        } catch (Exception e) {
            log.debug("AOEE: failed to get neighbors for ({}) --[{}]-->: {}",
                    src, edgeType, e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    public boolean contains(long src, String edgeType, long dst) {
        try {
            // AOEE proxy returns: {"exists": true/false}
            var result = restClient.get()
                    .uri("/api/edges/{src}/{edgeType}/contains/{dst}", src, edgeType, dst)
                    .retrieve()
                    .body(Map.class);
            if (result == null) return false;
            Object exists = result.get("exists");
            return exists instanceof Boolean b && b;
        } catch (Exception e) {
            log.debug("AOEE: failed to check edge ({}) --[{}]--> ({}): {}",
                    src, edgeType, dst, e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public long count(long src, String edgeType) {
        try {
            // AOEE proxy returns: {"count": N}
            var result = restClient.get()
                    .uri("/api/edges/{src}/{edgeType}/count", src, edgeType)
                    .retrieve()
                    .body(Map.class);
            if (result == null) return 0L;
            Object count = result.get("count");
            return count instanceof Number n ? n.longValue() : 0L;
        } catch (Exception e) {
            log.debug("AOEE: failed to count edges for ({}) --[{}]-->: {}",
                    src, edgeType, e.getMessage());
            return 0L;
        }
    }

    public boolean isAvailable() {
        try {
            restClient.get().uri("/actuator/health").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
