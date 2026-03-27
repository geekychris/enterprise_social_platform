package com.social.app.service;

import com.social.app.persistence.entity.OrgAssignmentEntity;
import com.social.app.persistence.entity.OrgUnitEntity;
import com.social.app.persistence.entity.PostEntity;
import com.social.app.persistence.entity.UserEntity;
import com.social.app.persistence.repository.OrgAssignmentRepository;
import com.social.app.persistence.repository.OrgUnitRepository;
import com.social.app.persistence.repository.PostRepository;
import com.social.app.persistence.repository.UserRepository;
import com.social.core.dto.OrgAssignmentDto;
import com.social.core.dto.OrgUnitDto;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class OrgService {

    private final OrgUnitRepository orgUnitRepository;
    private final OrgAssignmentRepository orgAssignmentRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final GlobalIdGenerator idGenerator;

    public OrgService(OrgUnitRepository orgUnitRepository,
                      OrgAssignmentRepository orgAssignmentRepository,
                      UserRepository userRepository,
                      PostRepository postRepository,
                      GlobalIdGenerator idGenerator) {
        this.orgUnitRepository = orgUnitRepository;
        this.orgAssignmentRepository = orgAssignmentRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.idGenerator = idGenerator;
    }

    // ── Org Unit CRUD ───────────────────────────────────────────────────

    @Transactional
    public OrgUnitEntity createUnit(String name, String type, Long parentId,
                                     Long headUserId, String description, String costCenter) {
        var entity = new OrgUnitEntity();
        entity.setId(idGenerator.next(ObjectType.ORG_UNIT).value());
        entity.setName(name);
        entity.setType(type);
        entity.setParentId(parentId);
        entity.setHeadUserId(headUserId);
        entity.setDescription(description);
        entity.setCostCenter(costCenter);
        return orgUnitRepository.save(entity);
    }

    @Transactional
    public OrgUnitEntity updateUnit(Long id, String name, String type, Long parentId,
                                     Long headUserId, String description, String costCenter) {
        OrgUnitEntity entity = orgUnitRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Org unit not found: " + id));
        if (name != null) entity.setName(name);
        if (type != null) entity.setType(type);
        entity.setParentId(parentId);
        entity.setHeadUserId(headUserId);
        if (description != null) entity.setDescription(description);
        if (costCenter != null) entity.setCostCenter(costCenter);
        return orgUnitRepository.save(entity);
    }

    @Transactional
    public void deleteUnit(Long id) {
        orgUnitRepository.deleteById(id);
    }

    public OrgUnitDto getUnit(Long id) {
        OrgUnitEntity entity = orgUnitRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Org unit not found: " + id));
        return toUnitDto(entity);
    }

    public List<OrgUnitDto> getRoots() {
        return orgUnitRepository.findByParentIdIsNull().stream()
                .map(this::toUnitDto)
                .toList();
    }

    public List<OrgUnitDto> getChildren(Long parentId) {
        return orgUnitRepository.findByParentId(parentId).stream()
                .map(this::toUnitDto)
                .toList();
    }

    public List<OrgUnitDto> getTree() {
        return orgUnitRepository.findAll().stream()
                .map(this::toUnitDto)
                .toList();
    }

    public List<OrgUnitDto> searchUnits(String query) {
        return orgUnitRepository.searchByName(query).stream()
                .map(this::toUnitDto)
                .toList();
    }

    // ── Assignments ─────────────────────────────────────────────────────

    @Transactional
    public OrgAssignmentEntity assignUser(Long userId, Long orgUnitId, String title,
                                           String relationshipType, Long reportsToUserId,
                                           String level, LocalDate startDate) {
        var entity = new OrgAssignmentEntity();
        entity.setId(idGenerator.next(ObjectType.ORG_ASSIGNMENT).value());
        entity.setUserId(userId);
        entity.setOrgUnitId(orgUnitId);
        entity.setTitle(title);
        if (relationshipType != null) entity.setRelationshipType(relationshipType);
        entity.setReportsToUserId(reportsToUserId);
        entity.setLevel(level);
        entity.setStartDate(startDate);
        return orgAssignmentRepository.save(entity);
    }

    @Transactional
    public void removeAssignment(Long userId, Long orgUnitId) {
        orgAssignmentRepository.deleteByUserIdAndOrgUnitId(userId, orgUnitId);
    }

    public List<OrgAssignmentDto> getUserAssignments(Long userId) {
        return orgAssignmentRepository.findByUserId(userId).stream()
                .map(this::toAssignmentDto)
                .toList();
    }

    public List<OrgAssignmentDto> getUnitMembers(Long orgUnitId) {
        return orgAssignmentRepository.findByOrgUnitId(orgUnitId).stream()
                .map(this::toAssignmentDto)
                .toList();
    }

    public List<OrgAssignmentDto> getDirectReports(Long userId) {
        return orgAssignmentRepository.findByReportsToUserId(userId).stream()
                .map(this::toAssignmentDto)
                .toList();
    }

    public List<OrgAssignmentDto> getReportingChain(Long userId) {
        List<OrgAssignmentDto> chain = new ArrayList<>();
        Long currentUserId = userId;
        int maxDepth = 50;
        while (currentUserId != null && maxDepth-- > 0) {
            List<OrgAssignmentEntity> assignments = orgAssignmentRepository.findByUserIdAndRelationshipType(currentUserId, "SOLID");
            if (assignments.isEmpty()) break;
            OrgAssignmentEntity assignment = assignments.get(0);
            chain.add(toAssignmentDto(assignment));
            if (assignment.getReportsToUserId() == null || assignment.getReportsToUserId().equals(currentUserId)) {
                break;
            }
            currentUserId = assignment.getReportsToUserId();
        }
        return chain;
    }

    public String getTeamActivity(Long orgUnitId, Long userId) {
        List<OrgAssignmentEntity> members = orgAssignmentRepository.findByOrgUnitId(orgUnitId);
        if (members.isEmpty()) return "No members found in this org unit.";

        List<Long> memberUserIds = members.stream()
                .map(OrgAssignmentEntity::getUserId)
                .toList();

        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
        List<PostEntity> recentPosts = postRepository.findByAuthorIdInOrderByCreatedAtDesc(memberUserIds);

        StringBuilder sb = new StringBuilder();
        sb.append("Team activity for org unit ").append(orgUnitId).append(" (last 7 days):\n");

        int count = 0;
        for (PostEntity post : recentPosts) {
            if (post.getCreatedAt().isBefore(since)) continue;
            if (count >= 20) break;
            String authorName = userRepository.findById(post.getAuthorId())
                    .map(u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername())
                    .orElse("Unknown");
            sb.append("- ").append(authorName).append(": ");
            String content = post.getContent();
            if (content != null && content.length() > 150) {
                content = content.substring(0, 150) + "...";
            }
            sb.append(content).append("\n");
            count++;
        }

        if (count == 0) {
            sb.append("No recent posts from team members.");
        }

        return sb.toString();
    }

    // ── DTO Helpers ─────────────────────────────────────────────────────

    private OrgUnitDto toUnitDto(OrgUnitEntity entity) {
        int childCount = orgUnitRepository.findByParentId(entity.getId()).size();
        int memberCount = orgAssignmentRepository.findByOrgUnitId(entity.getId()).size();
        String headUserName = null;
        if (entity.getHeadUserId() != null) {
            Optional<UserEntity> headUser = userRepository.findById(entity.getHeadUserId());
            if (headUser.isPresent()) {
                headUserName = headUser.get().getDisplayName() != null
                        ? headUser.get().getDisplayName()
                        : headUser.get().getUsername();
            }
        }
        return new OrgUnitDto(
                entity.getId(),
                entity.getName(),
                entity.getType(),
                entity.getParentId(),
                entity.getHeadUserId(),
                headUserName,
                entity.getDescription(),
                entity.getCostCenter(),
                childCount,
                memberCount
        );
    }

    private OrgAssignmentDto toAssignmentDto(OrgAssignmentEntity entity) {
        String userName = null;
        String userAvatarUrl = null;
        Optional<UserEntity> user = userRepository.findById(entity.getUserId());
        if (user.isPresent()) {
            userName = user.get().getDisplayName() != null
                    ? user.get().getDisplayName()
                    : user.get().getUsername();
            userAvatarUrl = user.get().getAvatarUrl();
        }

        String orgUnitName = null;
        Optional<OrgUnitEntity> orgUnit = orgUnitRepository.findById(entity.getOrgUnitId());
        if (orgUnit.isPresent()) {
            orgUnitName = orgUnit.get().getName();
        }

        String reportsToUserName = null;
        if (entity.getReportsToUserId() != null) {
            Optional<UserEntity> reportsTo = userRepository.findById(entity.getReportsToUserId());
            if (reportsTo.isPresent()) {
                reportsToUserName = reportsTo.get().getDisplayName() != null
                        ? reportsTo.get().getDisplayName()
                        : reportsTo.get().getUsername();
            }
        }

        return new OrgAssignmentDto(
                entity.getId(),
                entity.getUserId(),
                userName,
                userAvatarUrl,
                entity.getOrgUnitId(),
                orgUnitName,
                entity.getTitle(),
                entity.getRelationshipType(),
                entity.getReportsToUserId(),
                reportsToUserName,
                entity.getLevel(),
                entity.getStartDate() != null ? entity.getStartDate().toString() : null
        );
    }
}
