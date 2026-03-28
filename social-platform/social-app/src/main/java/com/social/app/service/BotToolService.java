package com.social.app.service;

import com.social.app.persistence.entity.MessageEntity;
import com.social.app.persistence.entity.UserEntity;
import com.social.app.persistence.repository.*;
import com.social.core.dto.PostDto;
import com.social.core.dto.SearchResultDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tools the bot can use to gather context. All operations are scoped
 * to what the requesting user has visibility to.
 */
@Service
public class BotToolService {

    private final PostRepository postRepository;
    private final PostService postService;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final MembershipRepository membershipRepository;
    private final GroupRepository groupRepository;
    private final PageRepository pageRepository;
    private final FeedService feedService;
    private final OrgService orgService;
    private final OrgAssignmentRepository orgAssignmentRepository;
    private final OrgUnitRepository orgUnitRepository;

    private final int MAX_CHARS_PER_POST = 300;
    private final int MAX_POSTS = 10;
    private final int MAX_SEARCH_HITS = 8;
    private final int MAX_MESSAGES = 30;

    public BotToolService(PostRepository postRepository, PostService postService,
                          UserRepository userRepository, MessageRepository messageRepository,
                          MembershipRepository membershipRepository, GroupRepository groupRepository,
                          PageRepository pageRepository, FeedService feedService,
                          OrgService orgService, OrgAssignmentRepository orgAssignmentRepository,
                          OrgUnitRepository orgUnitRepository) {
        this.postRepository = postRepository;
        this.postService = postService;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.membershipRepository = membershipRepository;
        this.groupRepository = groupRepository;
        this.pageRepository = pageRepository;
        this.feedService = feedService;
        this.orgService = orgService;
        this.orgAssignmentRepository = orgAssignmentRepository;
        this.orgUnitRepository = orgUnitRepository;
    }

    /**
     * Get recent user-only messages (excluding bot) from a conversation.
     */
    public String getConversationHistoryUsersOnly(long conversationId, int maxMessages, long excludeUserId) {
        var messages = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                conversationId, PageRequest.of(0, maxMessages * 3)); // fetch extra to filter
        var filtered = messages.stream()
                .filter(m -> m.getSenderId() != excludeUserId)
                .limit(maxMessages)
                .collect(java.util.stream.Collectors.toList());
        java.util.Collections.reverse(filtered);

        StringBuilder sb = new StringBuilder();
        for (MessageEntity msg : filtered) {
            String name = userRepository.findById(msg.getSenderId())
                    .map(u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername())
                    .orElse("Unknown");
            sb.append(name).append(": ").append(truncate(msg.getContent(), 500)).append("\n");
        }
        return sb.toString();
    }

    /**
     * Get recent messages from a conversation, formatted as text.
     */
    public String getConversationHistory(long conversationId, int maxMessages) {
        var messages = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                conversationId, PageRequest.of(0, Math.min(maxMessages, MAX_MESSAGES)));
        var reversed = new java.util.ArrayList<>(messages);
        java.util.Collections.reverse(reversed);

        StringBuilder sb = new StringBuilder();
        for (MessageEntity msg : reversed) {
            String name = userRepository.findById(msg.getSenderId())
                    .map(u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername())
                    .orElse("Unknown");
            sb.append(name).append(": ").append(truncate(msg.getContent(), 500)).append("\n");
        }
        return sb.toString();
    }

    /**
     * Get posts from a group the user is a member of.
     */
    public String getGroupPosts(long groupId, long userId) {
        // Verify user is member
        var membership = membershipRepository.findByUserIdAndGroupId(userId, groupId);
        if (membership.isEmpty() || !"APPROVED".equals(membership.get().getStatus())) {
            return "(You don't have access to this group)";
        }

        var group = groupRepository.findById(groupId).orElse(null);
        String groupName = group != null ? group.getName() : "Unknown Group";

        var posts = postRepository.findByTargetIdOrderByCreatedAtDesc(groupId).stream()
                .limit(MAX_POSTS).toList();

        StringBuilder sb = new StringBuilder("Posts from group \"" + groupName + "\":\n\n");
        for (var post : posts) {
            PostDto dto = postService.toDto(post, userId);
            sb.append("- ").append(dto.author().displayName()).append(": ")
              .append(truncate(dto.content(), MAX_CHARS_PER_POST)).append("\n");
        }
        return sb.toString();
    }

    /**
     * Get posts from a page.
     */
    public String getPagePosts(long pageId, long userId) {
        var page = pageRepository.findById(pageId).orElse(null);
        String pageName = page != null ? page.getName() : "Unknown Page";

        var posts = postRepository.findByTargetIdOrderByCreatedAtDesc(pageId).stream()
                .limit(MAX_POSTS).toList();

        StringBuilder sb = new StringBuilder("Posts from page \"" + pageName + "\":\n\n");
        for (var post : posts) {
            PostDto dto = postService.toDto(post, userId);
            sb.append("- ").append(dto.author().displayName()).append(": ")
              .append(truncate(dto.content(), MAX_CHARS_PER_POST)).append("\n");
        }
        return sb.toString();
    }

    /**
     * Search content visible to the user.
     */
    public String search(String query, long userId) {
        // Use the search endpoint logic
        // For now, search posts by content using the repository
        var feedResponse = feedService.assembleFeed(userId, null, 50);
        var matching = feedResponse.posts().stream()
                .filter(p -> p.content().toLowerCase().contains(query.toLowerCase()))
                .limit(MAX_SEARCH_HITS)
                .toList();

        if (matching.isEmpty()) return "No results found for \"" + query + "\"";

        StringBuilder sb = new StringBuilder("Search results for \"" + query + "\":\n\n");
        for (var post : matching) {
            sb.append("- ").append(post.author().displayName()).append(": ")
              .append(truncate(post.content(), MAX_CHARS_PER_POST)).append("\n");
        }
        return sb.toString();
    }

    /**
     * Get a user's profile summary.
     */
    public String getUserProfile(long targetUserId) {
        return userRepository.findById(targetUserId).map(u -> {
            StringBuilder sb = new StringBuilder("Profile: " + u.getDisplayName() + " (@" + u.getUsername() + ")\n");
            if (u.getJobTitle() != null) sb.append("Title: ").append(u.getJobTitle()).append("\n");
            if (u.getDepartment() != null) sb.append("Department: ").append(u.getDepartment()).append("\n");
            if (u.getBio() != null) sb.append("Bio: ").append(truncate(u.getBio(), 300)).append("\n");
            if (u.getLocation() != null) sb.append("Location: ").append(u.getLocation()).append("\n");
            if (u.getSkills() != null) sb.append("Skills: ").append(u.getSkills()).append("\n");
            if (u.getInterests() != null) sb.append("Interests: ").append(u.getInterests()).append("\n");
            return sb.toString();
        }).orElse("User not found");
    }

    /**
     * Get the user's feed summary.
     */
    public String getUserFeed(long userId) {
        var feedResponse = feedService.assembleFeed(userId, null, MAX_POSTS);
        StringBuilder sb = new StringBuilder("Your recent feed:\n\n");
        for (var post : feedResponse.posts()) {
            sb.append("- ").append(post.author().displayName()).append(": ")
              .append(truncate(post.content(), MAX_CHARS_PER_POST)).append("\n");
        }
        return sb.toString();
    }

    /**
     * List groups the user belongs to.
     */
    public String getUserGroups(long userId) {
        var memberships = membershipRepository.findByUserIdAndStatus(userId, "APPROVED");
        if (memberships.isEmpty()) return "You are not a member of any groups.";

        StringBuilder sb = new StringBuilder("Your groups:\n");
        for (var m : memberships) {
            var group = groupRepository.findById(m.getGroupId());
            group.ifPresent(g -> sb.append("- ").append(g.getName()).append(" (ID: ").append(g.getId()).append(")\n"));
        }
        return sb.toString();
    }

    /**
     * List pages the user owns.
     */
    public String getUserPages(long userId) {
        var pages = pageRepository.findAll().stream()
                .filter(p -> p.getOwnerId() != null && p.getOwnerId().equals(userId))
                .toList();
        if (pages.isEmpty()) return "No pages owned.";

        StringBuilder sb = new StringBuilder("Your pages:\n");
        for (var p : pages) {
            sb.append("- ").append(p.getName()).append(" (ID: ").append(p.getId()).append(")\n");
        }
        return sb.toString();
    }

    /**
     * Find a group by name (fuzzy match) among the user's groups.
     * Returns group ID or null.
     */
    public Long findGroupByName(String nameQuery, long userId) {
        var memberships = membershipRepository.findByUserIdAndStatus(userId, "APPROVED");
        String q = nameQuery.toLowerCase();
        for (var m : memberships) {
            var group = groupRepository.findById(m.getGroupId()).orElse(null);
            if (group != null && group.getName().toLowerCase().contains(q)) {
                return group.getId();
            }
        }
        // Also try searching all groups by name
        var searchResults = groupRepository.searchByName("%" + nameQuery + "%");
        for (var group : searchResults) {
            // Check if user is a member
            var mem = membershipRepository.findByUserIdAndGroupId(userId, group.getId());
            if (mem.isPresent() && "APPROVED".equals(mem.get().getStatus())) {
                return group.getId();
            }
        }
        return null;
    }

    /**
     * Scan all user's groups and check if any group name appears in the message.
     */
    public Long findGroupByNameInMessage(String message, long userId) {
        var memberships = membershipRepository.findByUserIdAndStatus(userId, "APPROVED");
        for (var m : memberships) {
            var group = groupRepository.findById(m.getGroupId()).orElse(null);
            if (group != null) {
                // Check if group name (or significant part) appears in the message
                String gName = group.getName().toLowerCase();
                // Check full name or first word
                if (message.contains(gName)) return group.getId();
                String[] words = gName.split("\\s+");
                for (String word : words) {
                    if (word.length() >= 3 && message.contains(word) && !word.matches("(?i)(the|group|team|page)")) {
                        return group.getId();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Find a page by name (fuzzy match).
     */
    public Long findPageByName(String nameQuery) {
        var results = pageRepository.searchByName("%" + nameQuery + "%");
        return results.isEmpty() ? null : results.get(0).getId();
    }

    /**
     * Find a user by name.
     */
    public Long findUserByName(String nameQuery) {
        var results = userRepository.searchByUsernameOrDisplayName(nameQuery);
        return results.isEmpty() ? null : results.get(0).getId();
    }

    /**
     * Search users by name/title/department/skills.
     */
    /**
     * Search users by name, title, department, skills, etc.
     * Searches each word in the query independently for broader matches.
     */
    public String searchUsers(String query) {
        // Search for the full query first
        var users = userRepository.searchByUsernameOrDisplayName(query);

        // If no results, try individual significant words
        if (users.isEmpty()) {
            for (String word : query.split("\\s+")) {
                if (word.length() >= 3) {
                    var partial = userRepository.searchByUsernameOrDisplayName(word);
                    users.addAll(partial);
                }
            }
            // Deduplicate
            var seen = new java.util.HashSet<Long>();
            users = users.stream().filter(u -> seen.add(u.getId())).collect(java.util.stream.Collectors.toList());
        }

        if (users.isEmpty()) return "No users found matching \"" + query + "\"";

        StringBuilder sb = new StringBuilder("Users matching \"" + query + "\":\n");
        for (var u : users.stream().filter(u -> !u.isBot()).limit(8).toList()) {
            sb.append("- ").append(u.getDisplayName()).append(" (@").append(u.getUsername()).append(")");
            if (u.getJobTitle() != null) sb.append(" — ").append(u.getJobTitle());
            if (u.getDepartment() != null) sb.append(", ").append(u.getDepartment());
            if (u.getSkills() != null) sb.append(" [skills: ").append(truncate(u.getSkills(), 100)).append("]");
            sb.append("\n");
        }
        return sb.toString();
    }

    // ──────────────────────────────────────────────────────────────────
    // Org tools
    // ──────────────────────────────────────────────────────────────────

    /**
     * Get who reports to a specific person.
     */
    public String getDirectReports(String personName) {
        Long userId = findUserByName(personName);
        if (userId == null) return "Could not find user \"" + personName + "\"";

        var reports = orgAssignmentRepository.findByReportsToUserId(userId);
        if (reports.isEmpty()) {
            String name = userRepository.findById(userId).map(u -> u.getDisplayName()).orElse(personName);
            return name + " has no direct reports.";
        }

        String managerName = userRepository.findById(userId).map(u -> u.getDisplayName()).orElse(personName);
        StringBuilder sb = new StringBuilder("Direct reports of " + managerName + ":\n");
        for (var r : reports) {
            var user = userRepository.findById(r.getUserId()).orElse(null);
            if (user == null) continue;
            sb.append("- ").append(user.getDisplayName());
            if (r.getTitle() != null) sb.append(" — ").append(r.getTitle());
            if (r.getLevel() != null) sb.append(" (").append(r.getLevel()).append(")");
            if (r.getRelationshipType().equals("DOTTED")) sb.append(" [dotted line]");
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Get the reporting chain for a person (up to CEO).
     */
    public String getReportingChain(String personName) {
        Long userId = findUserByName(personName);
        if (userId == null) return "Could not find user \"" + personName + "\"";

        var chain = orgService.getReportingChain(userId);
        if (chain.isEmpty()) return personName + " has no reporting chain defined.";

        StringBuilder sb = new StringBuilder("Reporting chain for " + personName + " (upward):\n");
        for (var a : chain) {
            sb.append("  → ").append(a.userName()).append(" — ").append(a.title())
              .append(" (").append(a.level()).append(")\n");
        }
        return sb.toString();
    }

    /**
     * Get all members of an org unit by name.
     */
    public String getOrgUnitMembers(String unitName) {
        var units = orgUnitRepository.searchByName(unitName);
        if (units.isEmpty()) return "Could not find org unit \"" + unitName + "\"";

        var unit = units.get(0);
        var members = orgService.getUnitMembers(unit.getId());
        if (members.isEmpty()) return "Org unit \"" + unit.getName() + "\" has no members.";

        StringBuilder sb = new StringBuilder("Members of " + unit.getName() + " (" + unit.getType() + "):\n");
        for (var m : members) {
            sb.append("- ").append(m.userName()).append(" — ").append(m.title())
              .append(" (").append(m.level()).append(")");
            if ("DOTTED".equals(m.relationshipType())) sb.append(" [dotted line]");
            sb.append("\n");
        }
        // Also include members of child units
        var children = orgUnitRepository.findByParentId(unit.getId());
        for (var child : children) {
            var childMembers = orgService.getUnitMembers(child.getId());
            if (!childMembers.isEmpty()) {
                sb.append("\n").append(child.getName()).append(" (").append(child.getType()).append("):\n");
                for (var m : childMembers) {
                    sb.append("  - ").append(m.userName()).append(" — ").append(m.title()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    /**
     * Search org for people matching criteria: skills, title, location, etc.
     * within a specific person's org subtree or across the whole org.
     */
    public String searchOrg(String query, String scopePersonName) {
        // If scoped to a person's org, get their org unit and search within it
        List<Long> scopeUserIds = null;
        String scopeLabel = "the organization";

        if (scopePersonName != null) {
            Long scopeUserId = findUserByName(scopePersonName);
            if (scopeUserId != null) {
                // Get all people who report to this person (recursively)
                scopeUserIds = new java.util.ArrayList<>();
                collectOrgSubtree(scopeUserId, scopeUserIds, 0);
                scopeLabel = scopePersonName + "'s org";
            }
        }

        // Search users by the query
        var allMatches = userRepository.searchByUsernameOrDisplayName(query);
        // If no results, try individual words
        if (allMatches.isEmpty()) {
            for (String word : query.split("\\s+")) {
                if (word.length() >= 3) {
                    allMatches.addAll(userRepository.searchByUsernameOrDisplayName(word));
                }
            }
        }

        // Filter to scope if specified
        final List<Long> finalScope = scopeUserIds;
        var filtered = allMatches.stream()
                .filter(u -> !u.isBot())
                .filter(u -> finalScope == null || finalScope.contains(u.getId()))
                .distinct()
                .limit(15)
                .toList();

        if (filtered.isEmpty()) return "No people found matching \"" + query + "\" in " + scopeLabel;

        StringBuilder sb = new StringBuilder("People matching \"" + query + "\" in " + scopeLabel + ":\n");
        for (var u : filtered) {
            sb.append("- ").append(u.getDisplayName());
            if (u.getJobTitle() != null) sb.append(" — ").append(u.getJobTitle());
            if (u.getDepartment() != null) sb.append(", ").append(u.getDepartment());
            if (u.getLocation() != null) sb.append(" [").append(u.getLocation()).append("]");
            if (u.getSkills() != null) sb.append(" skills: ").append(truncate(u.getSkills(), 80));
            sb.append("\n");
            // Add their org assignment
            var assignments = orgAssignmentRepository.findByUserId(u.getId());
            for (var a : assignments) {
                var orgUnit = orgUnitRepository.findById(a.getOrgUnitId());
                sb.append("    ↳ ").append(a.getTitle()).append(" in ")
                  .append(orgUnit.map(ou -> ou.getName()).orElse("?"))
                  .append(" (").append(a.getLevel()).append(")\n");
            }
        }
        return sb.toString();
    }

    /**
     * Get the full org tree structure summary.
     */
    public String getOrgOverview() {
        var roots = orgUnitRepository.findByParentIdIsNull();
        if (roots.isEmpty()) return "No organizational structure defined.";

        StringBuilder sb = new StringBuilder("Organization structure:\n");
        for (var root : roots) {
            appendOrgTree(sb, root, 0);
        }
        return sb.toString();
    }

    private void appendOrgTree(StringBuilder sb, com.social.app.persistence.entity.OrgUnitEntity unit, int depth) {
        String indent = "  ".repeat(depth);
        long memberCount = orgAssignmentRepository.findByOrgUnitId(unit.getId()).size();
        sb.append(indent).append("- ").append(unit.getName()).append(" (").append(unit.getType()).append(")");
        if (memberCount > 0) sb.append(" — ").append(memberCount).append(" people");
        if (unit.getHeadUserId() != null) {
            userRepository.findById(unit.getHeadUserId())
                    .ifPresent(u -> sb.append(", led by ").append(u.getDisplayName()));
        }
        sb.append("\n");

        var children = orgUnitRepository.findByParentId(unit.getId());
        for (var child : children) {
            appendOrgTree(sb, child, depth + 1);
        }
    }

    private void collectOrgSubtree(long userId, List<Long> collected, int depth) {
        if (depth > 10 || collected.size() > 200) return; // safety
        collected.add(userId);
        var reports = orgAssignmentRepository.findByReportsToUserId(userId);
        for (var r : reports) {
            if (!collected.contains(r.getUserId())) {
                collectOrgSubtree(r.getUserId(), collected, depth + 1);
            }
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
