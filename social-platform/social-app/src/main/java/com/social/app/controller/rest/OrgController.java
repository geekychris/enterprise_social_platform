package com.social.app.controller.rest;

import com.social.app.persistence.entity.OrgAssignmentEntity;
import com.social.app.persistence.entity.OrgUnitEntity;
import com.social.app.persistence.entity.UserEntity;
import com.social.app.persistence.repository.UserRepository;
import com.social.app.service.OrgService;
import com.social.core.dto.OrgAssignmentDto;
import com.social.core.dto.OrgUnitDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/org")
public class OrgController {

    private final OrgService orgService;
    private final UserRepository userRepository;

    public OrgController(OrgService orgService, UserRepository userRepository) {
        this.orgService = orgService;
        this.userRepository = userRepository;
    }

    // ── Org Units ───────────────────────────────────────────────────────

    @GetMapping("/units")
    public ResponseEntity<List<OrgUnitDto>> getUnits(@RequestParam(value = "all", required = false) Boolean all) {
        if (Boolean.TRUE.equals(all)) {
            return ResponseEntity.ok(orgService.getTree());
        }
        return ResponseEntity.ok(orgService.getRoots());
    }

    @GetMapping("/units/{id}")
    public ResponseEntity<OrgUnitDto> getUnit(@PathVariable long id) {
        return ResponseEntity.ok(orgService.getUnit(id));
    }

    @GetMapping("/units/{id}/children")
    public ResponseEntity<List<OrgUnitDto>> getChildren(@PathVariable long id) {
        return ResponseEntity.ok(orgService.getChildren(id));
    }

    @GetMapping("/units/{id}/members")
    public ResponseEntity<List<OrgAssignmentDto>> getUnitMembers(@PathVariable long id) {
        return ResponseEntity.ok(orgService.getUnitMembers(id));
    }

    @PostMapping("/units")
    public ResponseEntity<OrgUnitDto> createUnit(@RequestBody Map<String, Object> body,
                                                  Authentication auth) {
        requireAdmin(auth);
        OrgUnitEntity entity = orgService.createUnit(
                (String) body.get("name"),
                (String) body.get("type"),
                toLong(body.get("parentId")),
                toLong(body.get("headUserId")),
                (String) body.get("description"),
                (String) body.get("costCenter")
        );
        return ResponseEntity.ok(orgService.getUnit(entity.getId()));
    }

    @PutMapping("/units/{id}")
    public ResponseEntity<OrgUnitDto> updateUnit(@PathVariable long id,
                                                  @RequestBody Map<String, Object> body,
                                                  Authentication auth) {
        requireAdmin(auth);
        orgService.updateUnit(
                id,
                (String) body.get("name"),
                (String) body.get("type"),
                toLong(body.get("parentId")),
                toLong(body.get("headUserId")),
                (String) body.get("description"),
                (String) body.get("costCenter")
        );
        return ResponseEntity.ok(orgService.getUnit(id));
    }

    @DeleteMapping("/units/{id}")
    public ResponseEntity<Void> deleteUnit(@PathVariable long id, Authentication auth) {
        requireAdmin(auth);
        orgService.deleteUnit(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/units/search")
    public ResponseEntity<List<OrgUnitDto>> searchUnits(@RequestParam("q") String query) {
        return ResponseEntity.ok(orgService.searchUnits(query));
    }

    // ── Assignments ─────────────────────────────────────────────────────

    @GetMapping("/assignments/user/{userId}")
    public ResponseEntity<List<OrgAssignmentDto>> getUserAssignments(@PathVariable long userId) {
        return ResponseEntity.ok(orgService.getUserAssignments(userId));
    }

    @PostMapping("/assignments")
    public ResponseEntity<OrgAssignmentDto> assignUser(@RequestBody Map<String, Object> body,
                                                        Authentication auth) {
        requireAdmin(auth);
        long userId = toLong(body.get("userId"));
        long orgUnitId = toLong(body.get("orgUnitId"));
        String title = (String) body.get("title");
        String relationshipType = (String) body.get("relationshipType");
        Long reportsToUserId = toLong(body.get("reportsToUserId"));
        String level = (String) body.get("level");
        LocalDate startDate = body.get("startDate") != null
                ? LocalDate.parse((String) body.get("startDate")) : null;

        OrgAssignmentEntity entity = orgService.assignUser(userId, orgUnitId, title,
                relationshipType, reportsToUserId, level, startDate);

        List<OrgAssignmentDto> assignments = orgService.getUserAssignments(userId);
        OrgAssignmentDto result = assignments.stream()
                .filter(a -> a.id() == entity.getId())
                .findFirst()
                .orElse(assignments.get(0));
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/assignments/{userId}/{orgUnitId}")
    public ResponseEntity<Void> removeAssignment(@PathVariable long userId,
                                                  @PathVariable long orgUnitId,
                                                  Authentication auth) {
        requireAdmin(auth);
        orgService.removeAssignment(userId, orgUnitId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/assignments/reports/{userId}")
    public ResponseEntity<List<OrgAssignmentDto>> getDirectReports(@PathVariable long userId) {
        return ResponseEntity.ok(orgService.getDirectReports(userId));
    }

    @GetMapping("/assignments/chain/{userId}")
    public ResponseEntity<List<OrgAssignmentDto>> getReportingChain(@PathVariable long userId) {
        return ResponseEntity.ok(orgService.getReportingChain(userId));
    }

    // ── My Team ─────────────────────────────────────────────────────────

    @GetMapping("/my-team")
    public ResponseEntity<Map<String, String>> getMyTeam(Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        List<OrgAssignmentDto> assignments = orgService.getUserAssignments(userId);
        if (assignments.isEmpty()) {
            return ResponseEntity.ok(Map.of("activity", "You are not assigned to any org unit."));
        }
        long orgUnitId = assignments.get(0).orgUnitId();
        String activity = orgService.getTeamActivity(orgUnitId, userId);
        return ResponseEntity.ok(Map.of("activity", activity));
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private void requireAdmin(Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    private static Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s && !s.isBlank()) return Long.parseLong(s);
        return null;
    }
}
