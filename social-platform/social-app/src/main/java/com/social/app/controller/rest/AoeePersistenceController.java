package com.social.app.controller.rest;

import com.social.app.persistence.entity.GraphEdgeEntity;
import com.social.app.persistence.entity.GraphEntityEntity;
import com.social.app.persistence.repository.GraphEdgeRepository;
import com.social.app.persistence.repository.GraphEntityRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AoeePersistenceController {

    private final GraphEdgeRepository edgeRepository;
    private final GraphEntityRepository entityRepository;

    public AoeePersistenceController(GraphEdgeRepository edgeRepository,
                                     GraphEntityRepository entityRepository) {
        this.edgeRepository = edgeRepository;
        this.entityRepository = entityRepository;
    }

    // --- DTOs ---

    public record EdgeRequest(long src, String edgeType, long dst, Long timestampNs, Integer metadata) {}

    public record EdgeResponse(long id, long srcId, String edgeType, long dstId, long timestampNs,
                                int metadata, String createdAt) {
        static EdgeResponse from(GraphEdgeEntity e) {
            return new EdgeResponse(e.getId(), e.getSrcId(), e.getEdgeType(), e.getDstId(),
                    e.getTimestampNs(), e.getMetadata() != null ? e.getMetadata() : 0,
                    e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        }
    }

    public record ExistsResponse(boolean exists) {}
    public record CountResponse(long count) {}
    public record BatchEdgeResponse(long edgesCreated) {}

    public record EntityRequest(long id, String entityType, String name) {}

    public record EntityResponse(long id, String entityType, String name, String createdAt) {
        static EntityResponse from(GraphEntityEntity e) {
            return new EntityResponse(e.getId(), e.getEntityType(), e.getName(),
                    e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        }
    }

    public record BatchEntityResponse(long entitiesCreated) {}

    // --- Edge Endpoints ---

    @PostMapping("/edges")
    public ResponseEntity<EdgeResponse> createEdge(@RequestBody EdgeRequest req) {
        GraphEdgeEntity entity = new GraphEdgeEntity();
        entity.setSrcId(req.src());
        entity.setEdgeType(req.edgeType());
        entity.setDstId(req.dst());
        entity.setTimestampNs(req.timestampNs() != null ? req.timestampNs() : 0L);
        entity.setMetadata(req.metadata() != null ? req.metadata().shortValue() : 0);
        GraphEdgeEntity saved = edgeRepository.save(entity);
        return ResponseEntity.ok(EdgeResponse.from(saved));
    }

    @DeleteMapping("/edges/{src}/{edgeType}/{dst}")
    public ResponseEntity<Void> deleteEdge(@PathVariable long src,
                                           @PathVariable String edgeType,
                                           @PathVariable long dst) {
        edgeRepository.deleteBySrcIdAndEdgeTypeAndDstId(src, edgeType, dst);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/edges/{src}/{edgeType}/{dst}")
    public ResponseEntity<EdgeResponse> getEdge(@PathVariable long src,
                                                @PathVariable String edgeType,
                                                @PathVariable long dst) {
        return edgeRepository.findBySrcIdAndEdgeTypeAndDstId(src, edgeType, dst)
                .map(e -> ResponseEntity.ok(EdgeResponse.from(e)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/edges/{src}/{edgeType}/{dst}/exists")
    public ResponseEntity<ExistsResponse> edgeExists(@PathVariable long src,
                                                     @PathVariable String edgeType,
                                                     @PathVariable long dst) {
        boolean exists = edgeRepository.existsBySrcIdAndEdgeTypeAndDstId(src, edgeType, dst);
        return ResponseEntity.ok(new ExistsResponse(exists));
    }

    @GetMapping("/edges")
    public ResponseEntity<List<EdgeResponse>> getEdges(@RequestParam("src") long srcId,
                                                       @RequestParam("type") String edgeType,
                                                       @RequestParam(value = "limit", defaultValue = "1000") int limit) {
        List<GraphEdgeEntity> edges = edgeRepository.findBySrcIdAndEdgeType(srcId, edgeType,
                PageRequest.of(0, limit));
        List<EdgeResponse> response = edges.stream().map(EdgeResponse::from).toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/edges/count")
    public ResponseEntity<CountResponse> countEdges(@RequestParam("src") long srcId,
                                                    @RequestParam("type") String edgeType) {
        long count = edgeRepository.countBySrcIdAndEdgeType(srcId, edgeType);
        return ResponseEntity.ok(new CountResponse(count));
    }

    @PostMapping("/edges/batch")
    public ResponseEntity<BatchEdgeResponse> batchCreateEdges(@RequestBody List<EdgeRequest> requests) {
        List<GraphEdgeEntity> entities = requests.stream().map(req -> {
            GraphEdgeEntity entity = new GraphEdgeEntity();
            entity.setSrcId(req.src());
            entity.setEdgeType(req.edgeType());
            entity.setDstId(req.dst());
            entity.setTimestampNs(req.timestampNs() != null ? req.timestampNs() : 0L);
            entity.setMetadata(req.metadata() != null ? req.metadata().shortValue() : 0);
            return entity;
        }).toList();
        List<GraphEdgeEntity> saved = edgeRepository.saveAll(entities);
        return ResponseEntity.ok(new BatchEdgeResponse(saved.size()));
    }

    // --- Entity Endpoints ---

    @PostMapping("/entities")
    public ResponseEntity<EntityResponse> createEntity(@RequestBody EntityRequest req) {
        GraphEntityEntity entity = new GraphEntityEntity();
        entity.setId(req.id());
        entity.setEntityType(req.entityType());
        entity.setName(req.name());
        GraphEntityEntity saved = entityRepository.save(entity);
        return ResponseEntity.ok(EntityResponse.from(saved));
    }

    @PostMapping("/entities/batch")
    public ResponseEntity<BatchEntityResponse> batchCreateEntities(@RequestBody List<EntityRequest> requests) {
        List<GraphEntityEntity> entities = requests.stream().map(req -> {
            GraphEntityEntity entity = new GraphEntityEntity();
            entity.setId(req.id());
            entity.setEntityType(req.entityType());
            entity.setName(req.name());
            return entity;
        }).toList();
        List<GraphEntityEntity> saved = entityRepository.saveAll(entities);
        return ResponseEntity.ok(new BatchEntityResponse(saved.size()));
    }

    // --- Export/Stats ---

    @GetMapping("/export/stats")
    public ResponseEntity<Map<String, Object>> exportStats() {
        long totalEntities = entityRepository.count();
        List<String> entityTypes = entityRepository.findDistinctEntityTypes();

        long totalEdges = edgeRepository.count();
        List<String> edgeTypes = edgeRepository.findDistinctEdgeTypes();

        return ResponseEntity.ok(Map.of(
                "entities", Map.of("total", totalEntities, "types", entityTypes),
                "edges", Map.of("total", totalEdges, "types", edgeTypes)
        ));
    }
}
