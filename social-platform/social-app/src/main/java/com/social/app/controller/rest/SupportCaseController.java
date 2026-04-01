package com.social.app.controller.rest;

import com.social.app.persistence.entity.SupportCaseEntity;
import com.social.app.persistence.repository.SupportCaseRepository;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cases")
public class SupportCaseController {

    private final SupportCaseRepository caseRepository;
    private final GlobalIdGenerator idGenerator;
    private final JdbcTemplate jdbc;

    public SupportCaseController(SupportCaseRepository caseRepository,
                                  GlobalIdGenerator idGenerator,
                                  JdbcTemplate jdbc) {
        this.caseRepository = caseRepository;
        this.idGenerator = idGenerator;
        this.jdbc = jdbc;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listCases(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long appId) {
        List<SupportCaseEntity> cases;
        List<String> statuses = (status != null && !status.isEmpty() && !"ALL".equals(status))
                ? List.of(status)
                : List.of("OPEN", "IN_PROGRESS", "WAITING", "RESOLVED", "CLOSED");

        if (appId != null) {
            cases = caseRepository.findByAppIdAndStatusIn(appId, statuses);
        } else {
            cases = caseRepository.findByStatusInOrderByCreatedAtDesc(statuses);
        }
        return ResponseEntity.ok(cases.stream().map(this::toCaseMap).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCase(@PathVariable long id) {
        return caseRepository.findById(id)
                .map(c -> ResponseEntity.ok(toCaseMap(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createCase(@RequestBody Map<String, Object> body, Authentication auth) {
        long userId = (Long) auth.getPrincipal();

        SupportCaseEntity entity = new SupportCaseEntity();
        entity.setId(idGenerator.next(ObjectType.SUPPORT_CASE).value());
        entity.setTenantId(1L);
        entity.setTitle((String) body.get("title"));
        entity.setDescription((String) body.get("description"));
        entity.setRequesterId(userId);

        if (body.get("priority") != null) {
            entity.setPriority((String) body.get("priority"));
        }
        if (body.get("appId") != null) {
            entity.setAppId(((Number) body.get("appId")).longValue());
        }
        if (body.get("installationId") != null) {
            entity.setInstallationId(((Number) body.get("installationId")).longValue());
        }
        if (body.get("sourcePostId") != null) {
            entity.setSourcePostId(((Number) body.get("sourcePostId")).longValue());
        }
        if (body.get("sourceCommentId") != null) {
            entity.setSourceCommentId(((Number) body.get("sourceCommentId")).longValue());
        }

        // Generate case number from sequence
        Long seqVal = jdbc.queryForObject("SELECT nextval('case_number_seq')", Long.class);
        entity.setCaseNumber("CASE-" + seqVal);

        SupportCaseEntity saved = caseRepository.save(entity);
        return ResponseEntity.ok(toCaseMap(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCase(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return caseRepository.findById(id).map(c -> {
            if (body.get("title") != null) c.setTitle((String) body.get("title"));
            if (body.get("description") != null) c.setDescription((String) body.get("description"));
            if (body.get("status") != null) c.setStatus((String) body.get("status"));
            if (body.get("priority") != null) c.setPriority((String) body.get("priority"));
            if (body.get("metadata") != null) c.setMetadata(body.get("metadata").toString());

            SupportCaseEntity saved = caseRepository.save(c);
            return ResponseEntity.ok(toCaseMap(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<?> assignCase(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return caseRepository.findById(id).map(c -> {
            Object val = body.get("assigneeId");
            long assigneeId = val instanceof Number ? ((Number) val).longValue() : Long.parseLong(val.toString());
            c.setAssigneeId(assigneeId);
            if ("OPEN".equals(c.getStatus())) {
                c.setStatus("IN_PROGRESS");
            }
            SupportCaseEntity saved = caseRepository.save(c);
            return ResponseEntity.ok(toCaseMap(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<?> resolveCase(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return caseRepository.findById(id).map(c -> {
            c.setStatus("RESOLVED");
            c.setResolvedAt(Instant.now());
            if (body.get("resolution") != null) {
                c.setMetadata("{\"resolution\":\"" + body.get("resolution").toString().replace("\"", "\\\"") + "\"}");
            }
            SupportCaseEntity saved = caseRepository.save(c);
            return ResponseEntity.ok(toCaseMap(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getCaseStats() {
        long open = caseRepository.countByStatus("OPEN");
        long inProgress = caseRepository.countByStatus("IN_PROGRESS");
        long waiting = caseRepository.countByStatus("WAITING");
        long resolved = caseRepository.countByStatus("RESOLVED");
        long closed = caseRepository.countByStatus("CLOSED");

        return ResponseEntity.ok(Map.of(
                "open", open,
                "inProgress", inProgress,
                "waiting", waiting,
                "resolved", resolved,
                "closed", closed,
                "total", open + inProgress + waiting + resolved + closed
        ));
    }

    private Map<String, Object> toCaseMap(SupportCaseEntity c) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", c.getId());
        map.put("caseNumber", c.getCaseNumber());
        map.put("title", c.getTitle());
        map.put("description", c.getDescription());
        map.put("status", c.getStatus());
        map.put("priority", c.getPriority());
        map.put("requesterId", c.getRequesterId());
        map.put("assigneeId", c.getAssigneeId());
        map.put("appId", c.getAppId());
        map.put("sourcePostId", c.getSourcePostId());
        map.put("sourceCommentId", c.getSourceCommentId());
        map.put("metadata", c.getMetadata());
        map.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
        map.put("updatedAt", c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : null);
        map.put("resolvedAt", c.getResolvedAt() != null ? c.getResolvedAt().toString() : null);
        return map;
    }
}
