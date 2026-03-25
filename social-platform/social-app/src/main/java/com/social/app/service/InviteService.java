package com.social.app.service;

import com.social.app.config.JwtUtil;
import com.social.app.persistence.entity.InviteTokenEntity;
import com.social.app.persistence.entity.MembershipEntity;
import com.social.app.persistence.entity.UserEntity;
import com.social.app.persistence.repository.GroupRepository;
import com.social.app.persistence.repository.InviteTokenRepository;
import com.social.app.persistence.repository.MembershipRepository;
import com.social.app.persistence.repository.UserRepository;
import com.social.core.dto.AuthResponse;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class InviteService {

    private static final Logger log = LoggerFactory.getLogger(InviteService.class);

    private final UserService userService;
    private final UserRepository userRepository;
    private final InviteTokenRepository inviteTokenRepository;
    private final MembershipRepository membershipRepository;
    private final GroupRepository groupRepository;
    private final GlobalIdGenerator idGenerator;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${social.invite.expiry-hours:168}")
    private int expiryHours;

    @Value("${social.invite.base-url:}")
    private String baseUrl;

    public InviteService(UserService userService,
                         UserRepository userRepository,
                         InviteTokenRepository inviteTokenRepository,
                         MembershipRepository membershipRepository,
                         GroupRepository groupRepository,
                         GlobalIdGenerator idGenerator,
                         PasswordEncoder passwordEncoder,
                         JwtUtil jwtUtil) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.inviteTokenRepository = inviteTokenRepository;
        this.membershipRepository = membershipRepository;
        this.groupRepository = groupRepository;
        this.idGenerator = idGenerator;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public Map<String, Object> createInvitedUser(String email, String displayName,
                                                  String department, String jobTitle,
                                                  List<Long> groupIds, boolean admin,
                                                  long createdByUserId) {
        // Check if email already exists
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("A user with email " + email + " already exists");
        }

        UserEntity user = userService.createInvited(email, displayName, department, jobTitle, admin);

        // Add to groups
        if (groupIds != null) {
            for (Long groupId : groupIds) {
                if (groupRepository.existsById(groupId)) {
                    var membership = new MembershipEntity();
                    membership.setUserId(user.getId());
                    membership.setGroupId(groupId);
                    membership.setRole("MEMBER");
                    membership.setStatus("APPROVED");
                    membershipRepository.save(membership);
                }
            }
        }

        // Create invite token
        String token = generateToken(user.getId(), createdByUserId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.getId());
        result.put("email", email);
        result.put("displayName", displayName);
        result.put("inviteToken", token);
        result.put("inviteUrl", buildInviteUrl(token));
        return result;
    }

    @Transactional
    public List<Map<String, Object>> batchCreateInvitedUsers(List<Map<String, String>> rows,
                                                              long createdByUserId) {
        List<Map<String, Object>> results = new ArrayList<>();
        int rowNum = 0;
        for (Map<String, String> row : rows) {
            rowNum++;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("row", rowNum);
            result.put("email", row.getOrDefault("email", ""));

            try {
                String email = row.get("email");
                String displayName = row.getOrDefault("displayName", email);
                String department = row.getOrDefault("department", null);
                String jobTitle = row.getOrDefault("jobTitle", null);
                boolean isAdmin = "true".equalsIgnoreCase(row.getOrDefault("admin", "false"));

                // Resolve group names to IDs
                List<Long> groupIds = new ArrayList<>();
                String groupsStr = row.getOrDefault("groups", "");
                if (!groupsStr.isBlank()) {
                    for (String groupName : groupsStr.split(";")) {
                        String trimmed = groupName.trim();
                        if (!trimmed.isEmpty()) {
                            groupRepository.searchByName(trimmed).stream()
                                    .findFirst()
                                    .ifPresent(g -> groupIds.add(g.getId()));
                        }
                    }
                }

                Map<String, Object> created = createInvitedUser(
                        email, displayName, department, jobTitle, groupIds, isAdmin, createdByUserId);
                result.put("status", "created");
                result.put("inviteUrl", created.get("inviteUrl"));
                result.put("userId", created.get("userId"));
            } catch (Exception e) {
                result.put("status", "failed");
                result.put("error", e.getMessage());
            }
            results.add(result);
        }
        return results;
    }

    public Map<String, Object> validateToken(String tokenStr) {
        Map<String, Object> result = new LinkedHashMap<>();
        var optToken = inviteTokenRepository.findByToken(tokenStr);
        if (optToken.isEmpty()) {
            result.put("valid", false);
            result.put("error", "Invalid invite link");
            return result;
        }

        InviteTokenEntity invite = optToken.get();
        if (invite.isUsed()) {
            result.put("valid", false);
            result.put("used", true);
            result.put("error", "This invite link has already been used");
            return result;
        }
        if (invite.isExpired()) {
            result.put("valid", false);
            result.put("expired", true);
            result.put("error", "This invite link has expired");
            return result;
        }

        UserEntity user = userRepository.findById(invite.getUserId())
                .orElseThrow(() -> new IllegalStateException("Invite user not found"));

        result.put("valid", true);
        result.put("email", user.getEmail());
        result.put("displayName", user.getDisplayName());
        result.put("department", user.getDepartment());
        result.put("jobTitle", user.getJobTitle());
        return result;
    }

    @Transactional
    public AuthResponse redeemToken(String tokenStr, String username, String password, String bio) {
        InviteTokenEntity invite = inviteTokenRepository.findByToken(tokenStr)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite token"));

        if (invite.isUsed()) {
            throw new IllegalArgumentException("This invite has already been used");
        }
        if (invite.isExpired()) {
            throw new IllegalArgumentException("This invite has expired");
        }

        // Check username uniqueness
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username '" + username + "' is already taken");
        }

        UserEntity user = userRepository.findById(invite.getUserId())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        if (bio != null && !bio.isBlank()) {
            user.setBio(bio);
        }
        userRepository.save(user);

        invite.setUsedAt(Instant.now());
        inviteTokenRepository.save(invite);

        String jwt = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new AuthResponse(jwt, user.getId(), user.getUsername(), user.isAdmin());
    }

    @Transactional
    public String regenerateToken(long userId, long createdByUserId) {
        return generateToken(userId, createdByUserId);
    }

    @Transactional
    public void revokeToken(long tokenId) {
        inviteTokenRepository.deleteById(tokenId);
    }

    public List<Map<String, Object>> listInvites() {
        List<Map<String, Object>> results = new ArrayList<>();
        for (InviteTokenEntity invite : inviteTokenRepository.findAllByOrderByCreatedAtDesc()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", invite.getId());
            row.put("userId", invite.getUserId());
            row.put("token", invite.getToken());
            row.put("inviteUrl", buildInviteUrl(invite.getToken()));
            row.put("createdBy", invite.getCreatedBy());
            row.put("createdAt", invite.getCreatedAt());
            row.put("expiresAt", invite.getExpiresAt());
            row.put("usedAt", invite.getUsedAt());

            String status = invite.isUsed() ? "USED" : invite.isExpired() ? "EXPIRED" : "PENDING";
            row.put("status", status);

            userRepository.findById(invite.getUserId()).ifPresent(u -> {
                row.put("email", u.getEmail());
                row.put("displayName", u.getDisplayName());
                row.put("username", u.getUsername());
            });

            results.add(row);
        }
        return results;
    }

    private String generateToken(long userId, long createdByUserId) {
        String token = UUID.randomUUID().toString();
        var entity = new InviteTokenEntity();
        entity.setId(idGenerator.next(ObjectType.INVITE_TOKEN).value());
        entity.setUserId(userId);
        entity.setToken(token);
        entity.setExpiresAt(Instant.now().plus(expiryHours, ChronoUnit.HOURS));
        entity.setCreatedBy(createdByUserId);
        inviteTokenRepository.save(entity);
        return token;
    }

    private String buildInviteUrl(String token) {
        String base = baseUrl.isEmpty() ? "" : baseUrl;
        return base + "/setup/" + token;
    }
}
