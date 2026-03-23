package com.social.app.controller.rest;

import com.social.app.persistence.entity.TeamEntity;
import com.social.app.persistence.repository.MembershipRepository;
import com.social.app.persistence.repository.TeamRepository;
import com.social.core.dto.TeamDto;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import com.social.core.model.Visibility;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamRepository teamRepository;
    private final MembershipRepository membershipRepository;
    private final GlobalIdGenerator idGenerator;

    public TeamController(TeamRepository teamRepository,
                          MembershipRepository membershipRepository,
                          GlobalIdGenerator idGenerator) {
        this.teamRepository = teamRepository;
        this.membershipRepository = membershipRepository;
        this.idGenerator = idGenerator;
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeamDto> getTeam(@PathVariable long id) {
        return teamRepository.findById(id)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<TeamDto>> getAllTeams() {
        List<TeamDto> teams = teamRepository.findAll().stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(teams);
    }

    @PostMapping
    public ResponseEntity<TeamDto> createTeam(@RequestBody Map<String, String> body) {
        var entity = new TeamEntity();
        entity.setId(idGenerator.next(ObjectType.TEAM).value());
        entity.setName(body.get("name"));
        entity.setSlug(body.get("slug"));
        entity.setDescription(body.get("description"));
        entity.setVisibility(body.getOrDefault("visibility", "PUBLIC"));
        TeamEntity saved = teamRepository.save(entity);
        return ResponseEntity.ok(toDto(saved));
    }

    private TeamDto toDto(TeamEntity entity) {
        long memberCount = membershipRepository.countByGroupId(entity.getId());
        return new TeamDto(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                entity.getDescription(),
                Visibility.valueOf(entity.getVisibility()),
                memberCount
        );
    }
}
