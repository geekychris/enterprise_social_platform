package com.social.app.controller.rest;

import com.social.app.persistence.entity.UserEntity;
import com.social.app.persistence.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final GroupRepository groupRepository;
    private final PageRepository pageRepository;
    private final MessageRepository messageRepository;
    private final ReactionRepository reactionRepository;
    private final AttachmentRepository attachmentRepository;
    private final MembershipRepository membershipRepository;
    private final JdbcTemplate jdbcTemplate;

    @Value("${social.upload-dir:./uploads}")
    private String uploadDir;

    public AdminController(UserRepository userRepository,
                           PostRepository postRepository,
                           CommentRepository commentRepository,
                           GroupRepository groupRepository,
                           PageRepository pageRepository,
                           MessageRepository messageRepository,
                           ReactionRepository reactionRepository,
                           AttachmentRepository attachmentRepository,
                           MembershipRepository membershipRepository,
                           JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.groupRepository = groupRepository;
        this.pageRepository = pageRepository;
        this.messageRepository = messageRepository;
        this.reactionRepository = reactionRepository;
        this.attachmentRepository = attachmentRepository;
        this.membershipRepository = membershipRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    private void requireAdmin(Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    // ── Dashboard Overview ──────────────────────────────────────────────

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard(Authentication auth) {
        requireAdmin(auth);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("totalPosts", postRepository.count());
        stats.put("totalComments", commentRepository.count());
        stats.put("totalGroups", groupRepository.count());
        stats.put("totalPages", pageRepository.count());
        stats.put("totalMessages", messageRepository.count());
        stats.put("totalReactions", reactionRepository.count());
        stats.put("totalAttachments", attachmentRepository.count());

        Long activeUsersLast24h = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT author_id) FROM (" +
                "  SELECT author_id FROM posts WHERE created_at >= now() - interval '24 hours'" +
                "  UNION" +
                "  SELECT author_id FROM comments WHERE created_at >= now() - interval '24 hours'" +
                ") sub", Long.class);
        stats.put("activeUsersLast24h", activeUsersLast24h != null ? activeUsersLast24h : 0L);

        Long postsLast24h = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM posts WHERE created_at >= now() - interval '24 hours'", Long.class);
        stats.put("postsLast24h", postsLast24h != null ? postsLast24h : 0L);

        Long postsLast7d = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM posts WHERE created_at >= now() - interval '7 days'", Long.class);
        stats.put("postsLast7d", postsLast7d != null ? postsLast7d : 0L);

        Long postsLast30d = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM posts WHERE created_at >= now() - interval '30 days'", Long.class);
        stats.put("postsLast30d", postsLast30d != null ? postsLast30d : 0L);

        Long newUsersLast7d = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE created_at >= now() - interval '7 days'", Long.class);
        stats.put("newUsersLast7d", newUsersLast7d != null ? newUsersLast7d : 0L);

        Long newUsersLast30d = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE created_at >= now() - interval '30 days'", Long.class);
        stats.put("newUsersLast30d", newUsersLast30d != null ? newUsersLast30d : 0L);

        return ResponseEntity.ok(stats);
    }

    // ── DAU / MAU Metrics ──────────────────────────────────────────────

    @GetMapping("/analytics/dau-mau")
    public ResponseEntity<Map<String, Object>> dauMau(Authentication auth) {
        requireAdmin(auth);
        Map<String, Object> result = new LinkedHashMap<>();

        // DAU - distinct users who posted, commented, or reacted today
        Long dau = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT uid) FROM (" +
                "  SELECT author_id AS uid FROM posts WHERE created_at >= CURRENT_DATE" +
                "  UNION SELECT author_id FROM comments WHERE created_at >= CURRENT_DATE" +
                "  UNION SELECT user_id FROM reactions WHERE created_at >= CURRENT_DATE" +
                "  UNION SELECT sender_id FROM messages WHERE created_at >= CURRENT_DATE" +
                ") sub", Long.class);
        result.put("dau", dau != null ? dau : 0L);

        // WAU - last 7 days
        Long wau = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT uid) FROM (" +
                "  SELECT author_id AS uid FROM posts WHERE created_at >= now() - interval '7 days'" +
                "  UNION SELECT author_id FROM comments WHERE created_at >= now() - interval '7 days'" +
                "  UNION SELECT user_id FROM reactions WHERE created_at >= now() - interval '7 days'" +
                "  UNION SELECT sender_id FROM messages WHERE created_at >= now() - interval '7 days'" +
                ") sub", Long.class);
        result.put("wau", wau != null ? wau : 0L);

        // MAU - last 30 days
        Long mau = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT uid) FROM (" +
                "  SELECT author_id AS uid FROM posts WHERE created_at >= now() - interval '30 days'" +
                "  UNION SELECT author_id FROM comments WHERE created_at >= now() - interval '30 days'" +
                "  UNION SELECT user_id FROM reactions WHERE created_at >= now() - interval '30 days'" +
                "  UNION SELECT sender_id FROM messages WHERE created_at >= now() - interval '30 days'" +
                ") sub", Long.class);
        result.put("mau", mau != null ? mau : 0L);

        result.put("totalUsers", userRepository.count());

        // DAU trend (last 14 days)
        List<Map<String, Object>> dauTrend = jdbcTemplate.queryForList(
                "SELECT d.date, COALESCE(sub.active_users, 0) as active_users FROM " +
                "generate_series((now() - interval '14 days')::date, now()::date, '1 day') d(date) " +
                "LEFT JOIN (" +
                "  SELECT dt, COUNT(DISTINCT uid) as active_users FROM (" +
                "    SELECT date_trunc('day', created_at)::date as dt, author_id AS uid FROM posts WHERE created_at >= now() - interval '14 days'" +
                "    UNION ALL SELECT date_trunc('day', created_at)::date, author_id FROM comments WHERE created_at >= now() - interval '14 days'" +
                "    UNION ALL SELECT date_trunc('day', created_at)::date, user_id FROM reactions WHERE created_at >= now() - interval '14 days'" +
                "    UNION ALL SELECT date_trunc('day', created_at)::date, sender_id FROM messages WHERE created_at >= now() - interval '14 days'" +
                "  ) x GROUP BY dt" +
                ") sub ON sub.dt = d.date ORDER BY d.date");
        result.put("dauTrend", dauTrend);

        return ResponseEntity.ok(result);
    }

    // ── Content Breakdown ───────────────────────────────────────────────

    @GetMapping("/analytics/content-breakdown")
    public ResponseEntity<Map<String, Object>> contentBreakdown(Authentication auth) {
        requireAdmin(auth);
        Map<String, Object> result = new LinkedHashMap<>();

        // Reaction type distribution
        List<Map<String, Object>> reactionDist = jdbcTemplate.queryForList(
                "SELECT reaction_type, COUNT(*) as count FROM reactions GROUP BY reaction_type ORDER BY count DESC");
        result.put("reactionDistribution", reactionDist);

        // Post target type distribution (where are posts being made)
        List<Map<String, Object>> postTargets = jdbcTemplate.queryForList(
                "SELECT COALESCE(target_type, 'USER_FEED') as target_type, COUNT(*) as count FROM posts GROUP BY target_type ORDER BY count DESC");
        result.put("postTargetDistribution", postTargets);

        // Comment depth distribution
        List<Map<String, Object>> commentDepths = jdbcTemplate.queryForList(
                "SELECT depth, COUNT(*) as count FROM comments GROUP BY depth ORDER BY depth");
        result.put("commentDepthDistribution", commentDepths);

        // Average posts per user (active users only)
        Map<String, Object> postStats = new LinkedHashMap<>();
        Long totalPosts = postRepository.count();
        Long totalUsers = userRepository.count();
        Long usersWithPosts = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT author_id) FROM posts", Long.class);
        postStats.put("totalPosts", totalPosts);
        postStats.put("totalUsers", totalUsers);
        postStats.put("usersWithPosts", usersWithPosts != null ? usersWithPosts : 0);
        postStats.put("avgPostsPerActiveUser", usersWithPosts != null && usersWithPosts > 0
                ? Math.round((double) totalPosts / usersWithPosts * 10) / 10.0 : 0);
        postStats.put("userParticipationRate", totalUsers > 0
                ? Math.round((double) (usersWithPosts != null ? usersWithPosts : 0) / totalUsers * 100) : 0);
        result.put("postStats", postStats);

        // Reactions per post (average)
        Long totalReactions = reactionRepository.count();
        Long totalComments = commentRepository.count();
        result.put("avgReactionsPerPost", totalPosts > 0 ? Math.round((double) totalReactions / totalPosts * 10) / 10.0 : 0);
        result.put("avgCommentsPerPost", totalPosts > 0 ? Math.round((double) totalComments / totalPosts * 10) / 10.0 : 0);

        return ResponseEntity.ok(result);
    }

    // ── Hourly Activity Heatmap ────────────────────────────────────────

    @GetMapping("/analytics/hourly-activity")
    public ResponseEntity<List<Map<String, Object>>> hourlyActivity(Authentication auth) {
        requireAdmin(auth);
        List<Map<String, Object>> hourly = jdbcTemplate.queryForList(
                "SELECT EXTRACT(DOW FROM created_at)::int as day_of_week, " +
                "EXTRACT(HOUR FROM created_at)::int as hour, " +
                "COUNT(*) as count FROM posts " +
                "WHERE created_at >= now() - interval '30 days' " +
                "GROUP BY day_of_week, hour ORDER BY day_of_week, hour");
        return ResponseEntity.ok(hourly);
    }

    // ── Messaging Analytics ─────────────────────────────────────────────

    @GetMapping("/analytics/messaging")
    public ResponseEntity<Map<String, Object>> messagingAnalytics(Authentication auth) {
        requireAdmin(auth);
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("totalMessages", messageRepository.count());

        Long uniqueConversations = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT LEAST(sender_id, recipient_id) || '-' || GREATEST(sender_id, recipient_id)) FROM messages",
                Long.class);
        result.put("uniqueConversations", uniqueConversations != null ? uniqueConversations : 0);

        Long usersWhoMessaged = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT uid) FROM (SELECT sender_id as uid FROM messages UNION SELECT recipient_id FROM messages) sub",
                Long.class);
        result.put("usersWhoMessaged", usersWhoMessaged != null ? usersWhoMessaged : 0);

        // Messages per day trend (last 14 days)
        List<Map<String, Object>> msgTrend = jdbcTemplate.queryForList(
                "SELECT d.date, COALESCE(sub.msg_count, 0) as msg_count FROM " +
                "generate_series((now() - interval '14 days')::date, now()::date, '1 day') d(date) " +
                "LEFT JOIN (" +
                "  SELECT date_trunc('day', created_at)::date as dt, COUNT(*) as msg_count " +
                "  FROM messages WHERE created_at >= now() - interval '14 days' GROUP BY dt" +
                ") sub ON sub.dt = d.date ORDER BY d.date");
        result.put("messageTrend", msgTrend);

        return ResponseEntity.ok(result);
    }

    // ── Social Graph Stats ──────────────────────────────────────────────

    @GetMapping("/analytics/social-graph")
    public ResponseEntity<Map<String, Object>> socialGraphStats(Authentication auth) {
        requireAdmin(auth);
        Map<String, Object> result = new LinkedHashMap<>();

        Long totalFollows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM follows", Long.class);
        result.put("totalFollows", totalFollows != null ? totalFollows : 0);

        Long totalFriendRequests = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM friend_requests", Long.class);
        result.put("totalFriendRequests", totalFriendRequests != null ? totalFriendRequests : 0);

        Long pendingRequests = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM friend_requests WHERE status = 'PENDING'", Long.class);
        result.put("pendingFriendRequests", pendingRequests != null ? pendingRequests : 0);

        Long acceptedRequests = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM friend_requests WHERE status = 'ACCEPTED'", Long.class);
        result.put("acceptedFriendRequests", acceptedRequests != null ? acceptedRequests : 0);

        Long totalMemberships = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memberships WHERE status = 'APPROVED'", Long.class);
        result.put("totalMemberships", totalMemberships != null ? totalMemberships : 0);

        // Average follows per user
        Long totalUsers = userRepository.count();
        result.put("avgFollowsPerUser", totalUsers > 0 && totalFollows != null
                ? Math.round((double) totalFollows / totalUsers * 10) / 10.0 : 0);

        // Most connected users (by follow count)
        List<Map<String, Object>> mostFollowed = jdbcTemplate.queryForList(
                "SELECT u.id, u.username, u.display_name, u.avatar_url, COUNT(f.follower_id) as follower_count " +
                "FROM users u JOIN follows f ON u.id = f.followed_id " +
                "GROUP BY u.id, u.username, u.display_name, u.avatar_url " +
                "ORDER BY follower_count DESC LIMIT 10");
        result.put("mostFollowed", mostFollowed);

        return ResponseEntity.ok(result);
    }

    // ── Engagement Over Time (Group/Page/User) ──────────────────────────

    @GetMapping("/analytics/engagement")
    public ResponseEntity<Map<String, Object>> engagement(
            @RequestParam String entityType,
            @RequestParam String entityId,
            @RequestParam(defaultValue = "30") int days,
            Authentication auth) {
        requireAdmin(auth);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entityType", entityType);
        result.put("entityId", entityId);
        result.put("days", days);

        long id = Long.parseLong(entityId);

        if ("group".equalsIgnoreCase(entityType)) {
            // Group engagement: posts, comments, unique posters over time
            List<Map<String, Object>> activity = jdbcTemplate.queryForList(
                    "SELECT d.date, " +
                    "  COALESCE(p.cnt, 0) as posts, " +
                    "  COALESCE(p.authors, 0) as unique_posters " +
                    "FROM generate_series((now() - (? || ' days')::interval)::date, now()::date, '1 day') d(date) " +
                    "LEFT JOIN (" +
                    "  SELECT date_trunc('day', created_at)::date as dt, COUNT(*) as cnt, COUNT(DISTINCT author_id) as authors " +
                    "  FROM posts WHERE target_id = ? AND target_type = 'GROUP_FEED' AND created_at >= now() - (? || ' days')::interval " +
                    "  GROUP BY dt" +
                    ") p ON p.dt = d.date ORDER BY d.date",
                    days, id, days);
            result.put("activity", activity);

            // Member growth
            List<Map<String, Object>> memberGrowth = jdbcTemplate.queryForList(
                    "SELECT date_trunc('day', joined_at)::date as date, COUNT(*) as new_members " +
                    "FROM memberships WHERE group_id = ? AND joined_at >= now() - (? || ' days')::interval " +
                    "GROUP BY date ORDER BY date", id, days);
            result.put("memberGrowth", memberGrowth);

            // Group info
            List<Map<String, Object>> info = jdbcTemplate.queryForList(
                    "SELECT g.name, " +
                    "  (SELECT COUNT(*) FROM memberships WHERE group_id = g.id AND status = 'APPROVED') as members, " +
                    "  (SELECT COUNT(*) FROM posts WHERE target_id = g.id AND target_type = 'GROUP_FEED') as total_posts " +
                    "FROM groups_ g WHERE g.id = ?", id);
            if (!info.isEmpty()) result.put("info", info.get(0));

        } else if ("page".equalsIgnoreCase(entityType)) {
            List<Map<String, Object>> activity = jdbcTemplate.queryForList(
                    "SELECT d.date, COALESCE(p.cnt, 0) as posts " +
                    "FROM generate_series((now() - (? || ' days')::interval)::date, now()::date, '1 day') d(date) " +
                    "LEFT JOIN (" +
                    "  SELECT date_trunc('day', created_at)::date as dt, COUNT(*) as cnt " +
                    "  FROM posts WHERE target_id = ? AND target_type = 'PAGE_FEED' AND created_at >= now() - (? || ' days')::interval " +
                    "  GROUP BY dt" +
                    ") p ON p.dt = d.date ORDER BY d.date",
                    days, id, days);
            result.put("activity", activity);

            List<Map<String, Object>> info = jdbcTemplate.queryForList(
                    "SELECT pg.name, " +
                    "  (SELECT COUNT(*) FROM page_memberships WHERE page_id = pg.id AND status = 'APPROVED') as followers, " +
                    "  (SELECT COUNT(*) FROM posts WHERE target_id = pg.id AND target_type = 'PAGE_FEED') as total_posts " +
                    "FROM pages pg WHERE pg.id = ?", id);
            if (!info.isEmpty()) result.put("info", info.get(0));

        } else if ("user".equalsIgnoreCase(entityType)) {
            List<Map<String, Object>> activity = jdbcTemplate.queryForList(
                    "SELECT d.date, " +
                    "  COALESCE(p.cnt, 0) as posts, " +
                    "  COALESCE(c.cnt, 0) as comments, " +
                    "  COALESCE(r.cnt, 0) as reactions " +
                    "FROM generate_series((now() - (? || ' days')::interval)::date, now()::date, '1 day') d(date) " +
                    "LEFT JOIN (SELECT date_trunc('day', created_at)::date as dt, COUNT(*) as cnt FROM posts WHERE author_id = ? AND created_at >= now() - (? || ' days')::interval GROUP BY dt) p ON p.dt = d.date " +
                    "LEFT JOIN (SELECT date_trunc('day', created_at)::date as dt, COUNT(*) as cnt FROM comments WHERE author_id = ? AND created_at >= now() - (? || ' days')::interval GROUP BY dt) c ON c.dt = d.date " +
                    "LEFT JOIN (SELECT date_trunc('day', created_at)::date as dt, COUNT(*) as cnt FROM reactions WHERE user_id = ? AND created_at >= now() - (? || ' days')::interval GROUP BY dt) r ON r.dt = d.date " +
                    "ORDER BY d.date",
                    days, id, days, id, days, id, days);
            result.put("activity", activity);

            List<Map<String, Object>> info = jdbcTemplate.queryForList(
                    "SELECT u.username, u.display_name, " +
                    "  (SELECT COUNT(*) FROM posts WHERE author_id = u.id) as total_posts, " +
                    "  (SELECT COUNT(*) FROM comments WHERE author_id = u.id) as total_comments, " +
                    "  (SELECT COUNT(*) FROM follows WHERE followed_id = u.id) as followers " +
                    "FROM users u WHERE u.id = ?", id);
            if (!info.isEmpty()) result.put("info", info.get(0));
        }

        return ResponseEntity.ok(result);
    }

    // ── User Activity Analytics ─────────────────────────────────────────

    @GetMapping("/analytics/user-activity")
    public ResponseEntity<List<Map<String, Object>>> userActivity(Authentication auth) {
        requireAdmin(auth);

        List<Map<String, Object>> result = jdbcTemplate.queryForList(
                "SELECT d.date, " +
                "  COALESCE(p.count, 0) as posts, " +
                "  COALESCE(c.count, 0) as comments, " +
                "  COALESCE(r.count, 0) as reactions " +
                "FROM generate_series((now() - interval '30 days')::date, now()::date, '1 day') d(date) " +
                "LEFT JOIN (SELECT date_trunc('day', created_at)::date as date, COUNT(*) as count FROM posts WHERE created_at >= now() - interval '30 days' GROUP BY date) p ON p.date = d.date " +
                "LEFT JOIN (SELECT date_trunc('day', created_at)::date as date, COUNT(*) as count FROM comments WHERE created_at >= now() - interval '30 days' GROUP BY date) c ON c.date = d.date " +
                "LEFT JOIN (SELECT date_trunc('day', created_at)::date as date, COUNT(*) as count FROM reactions WHERE created_at >= now() - interval '30 days' GROUP BY date) r ON r.date = d.date " +
                "ORDER BY d.date");

        return ResponseEntity.ok(result);
    }

    // ── Top Users ───────────────────────────────────────────────────────

    @GetMapping("/analytics/top-users")
    public ResponseEntity<List<Map<String, Object>>> topUsers(
            @RequestParam(defaultValue = "7d") String period,
            @RequestParam(defaultValue = "10") int limit,
            Authentication auth) {
        requireAdmin(auth);

        String interval = switch (period) {
            case "24h" -> "24 hours";
            case "30d" -> "30 days";
            case "90d" -> "90 days";
            default -> "7 days";
        };

        List<Map<String, Object>> result = jdbcTemplate.queryForList(
                "SELECT p.author_id, u.username, u.display_name, u.avatar_url, COUNT(*) as post_count " +
                "FROM posts p JOIN users u ON u.id = p.author_id " +
                "WHERE p.created_at >= now() - interval '" + interval + "' " +
                "GROUP BY p.author_id, u.username, u.display_name, u.avatar_url " +
                "ORDER BY post_count DESC LIMIT ?", limit);

        return ResponseEntity.ok(result);
    }

    // ── Top Groups ──────────────────────────────────────────────────────

    @GetMapping("/analytics/top-groups")
    public ResponseEntity<List<Map<String, Object>>> topGroups(
            @RequestParam(defaultValue = "10") int limit,
            Authentication auth) {
        requireAdmin(auth);

        List<Map<String, Object>> result = jdbcTemplate.queryForList(
                "SELECT g.id, g.name, g.slug, " +
                "  (SELECT COUNT(*) FROM memberships m WHERE m.group_id = g.id AND m.status = 'APPROVED') as member_count, " +
                "  (SELECT COUNT(*) FROM posts p WHERE p.target_id = g.id AND p.target_type = 'GROUP_FEED') as post_count " +
                "FROM groups_ g " +
                "ORDER BY post_count DESC LIMIT ?", limit);

        return ResponseEntity.ok(result);
    }

    // ── Top Pages ───────────────────────────────────────────────────────

    @GetMapping("/analytics/top-pages")
    public ResponseEntity<List<Map<String, Object>>> topPages(
            @RequestParam(defaultValue = "10") int limit,
            Authentication auth) {
        requireAdmin(auth);

        List<Map<String, Object>> result = jdbcTemplate.queryForList(
                "SELECT pg.id, pg.name, pg.slug, " +
                "  (SELECT COUNT(*) FROM page_memberships pm WHERE pm.page_id = pg.id) as follower_count, " +
                "  (SELECT COUNT(*) FROM posts p WHERE p.target_id = pg.id AND p.target_type = 'PAGE_FEED') as post_count " +
                "FROM pages pg " +
                "ORDER BY post_count DESC LIMIT ?", limit);

        return ResponseEntity.ok(result);
    }

    // ── Growth Chart ────────────────────────────────────────────────────

    @GetMapping("/analytics/growth")
    public ResponseEntity<List<Map<String, Object>>> growth(Authentication auth) {
        requireAdmin(auth);

        List<Map<String, Object>> result = jdbcTemplate.queryForList(
                "SELECT date_trunc('week', created_at)::date as week, COUNT(*) as signups " +
                "FROM users WHERE created_at >= now() - interval '12 weeks' " +
                "GROUP BY week ORDER BY week");

        return ResponseEntity.ok(result);
    }

    // ── Content Moderation: Posts ────────────────────────────────────────

    @GetMapping("/posts")
    public ResponseEntity<List<Map<String, Object>>> listPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        requireAdmin(auth);

        List<Map<String, Object>> result = jdbcTemplate.queryForList(
                "SELECT p.id, p.author_id, u.username, u.display_name, p.content, " +
                "  p.target_type, p.target_id, p.visibility, p.created_at, p.updated_at " +
                "FROM posts p JOIN users u ON u.id = p.author_id " +
                "ORDER BY p.created_at DESC LIMIT ? OFFSET ?", size, page * size);

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable long id, Authentication auth) {
        requireAdmin(auth);
        postRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> deleteComment(@PathVariable long id, Authentication auth) {
        requireAdmin(auth);
        commentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── User Management ─────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String q,
            Authentication auth) {
        requireAdmin(auth);

        List<Map<String, Object>> result;
        if (q.isEmpty()) {
            result = jdbcTemplate.queryForList(
                    "SELECT id, username, display_name, email, avatar_url, bio, visibility, is_admin, created_at " +
                    "FROM users ORDER BY created_at DESC LIMIT ? OFFSET ?", size, page * size);
        } else {
            String pattern = "%" + q.toLowerCase() + "%";
            result = jdbcTemplate.queryForList(
                    "SELECT id, username, display_name, email, avatar_url, bio, visibility, is_admin, created_at " +
                    "FROM users WHERE LOWER(username) LIKE ? OR LOWER(display_name) LIKE ? OR LOWER(email) LIKE ? " +
                    "ORDER BY created_at DESC LIMIT ? OFFSET ?", pattern, pattern, pattern, size, page * size);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable long id, Authentication auth) {
        requireAdmin(auth);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, username, display_name, email, avatar_url, bio, visibility, is_admin, created_at, updated_at " +
                "FROM users WHERE id = ?", id);

        if (rows.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> user = new LinkedHashMap<>(rows.get(0));

        Long postCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM posts WHERE author_id = ?", Long.class, id);
        user.put("postCount", postCount != null ? postCount : 0L);

        Long commentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comments WHERE author_id = ?", Long.class, id);
        user.put("commentCount", commentCount != null ? commentCount : 0L);

        Long groupCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memberships WHERE user_id = ? AND status = 'APPROVED'", Long.class, id);
        user.put("groupCount", groupCount != null ? groupCount : 0L);

        Long followerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM follows WHERE followed_id = ?", Long.class, id);
        user.put("followerCount", followerCount != null ? followerCount : 0L);

        Long followingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM follows WHERE follower_id = ?", Long.class, id);
        user.put("followingCount", followingCount != null ? followingCount : 0L);

        return ResponseEntity.ok(user);
    }

    @PutMapping("/users/{id}/admin")
    public ResponseEntity<Map<String, Object>> toggleAdmin(@PathVariable long id, Authentication auth) {
        requireAdmin(auth);

        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        user.setAdmin(!user.isAdmin());
        userRepository.save(user);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("admin", user.isAdmin());
        return ResponseEntity.ok(result);
    }

    // ── Group Management ────────────────────────────────────────────────

    @GetMapping("/groups")
    public ResponseEntity<List<Map<String, Object>>> listGroups(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        requireAdmin(auth);

        List<Map<String, Object>> result = jdbcTemplate.queryForList(
                "SELECT g.id, g.name, g.slug, g.description, g.visibility, g.created_at, " +
                "  (SELECT COUNT(*) FROM memberships m WHERE m.group_id = g.id AND m.status = 'APPROVED') as member_count, " +
                "  (SELECT COUNT(*) FROM posts p WHERE p.target_id = g.id AND p.target_type = 'GROUP_FEED') as post_count " +
                "FROM groups_ g ORDER BY g.created_at DESC LIMIT ? OFFSET ?", size, page * size);

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/groups/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable long id, Authentication auth) {
        requireAdmin(auth);
        groupRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── Page Management ─────────────────────────────────────────────────

    @GetMapping("/pages")
    public ResponseEntity<List<Map<String, Object>>> listPages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        requireAdmin(auth);

        List<Map<String, Object>> result = jdbcTemplate.queryForList(
                "SELECT pg.id, pg.name, pg.slug, pg.description, pg.visibility, pg.created_at, " +
                "  (SELECT COUNT(*) FROM page_memberships pm WHERE pm.page_id = pg.id) as follower_count, " +
                "  (SELECT COUNT(*) FROM posts p WHERE p.target_id = pg.id AND p.target_type = 'PAGE_FEED') as post_count " +
                "FROM pages pg ORDER BY pg.created_at DESC LIMIT ? OFFSET ?", size, page * size);

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/pages/{id}")
    public ResponseEntity<Void> deletePage(@PathVariable long id, Authentication auth) {
        requireAdmin(auth);
        pageRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── System Health ───────────────────────────────────────────────────

    @GetMapping("/system")
    public ResponseEntity<Map<String, Object>> systemHealth(Authentication auth) {
        requireAdmin(auth);

        Map<String, Object> stats = new LinkedHashMap<>();

        // Upload directory stats
        Path uploadPath = Paths.get(uploadDir);
        long uploadDirSize = 0;
        long uploadFileCount = 0;
        if (Files.exists(uploadPath)) {
            try (Stream<Path> walk = Files.walk(uploadPath)) {
                var fileStats = walk.filter(Files::isRegularFile)
                        .mapToLong(p -> {
                            try {
                                return Files.size(p);
                            } catch (IOException e) {
                                return 0;
                            }
                        })
                        .summaryStatistics();
                uploadDirSize = fileStats.getSum();
                uploadFileCount = fileStats.getCount();
            } catch (IOException e) {
                // directory not accessible
            }
        }
        stats.put("uploadDirSize", uploadDirSize);
        stats.put("uploadFileCount", uploadFileCount);

        // Database size
        String dbSize = jdbcTemplate.queryForObject(
                "SELECT pg_size_pretty(pg_database_size(current_database()))", String.class);
        stats.put("databaseSize", dbSize);

        // Duplicate attachments
        Long duplicates = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" +
                "  SELECT content_hash FROM attachments WHERE content_hash IS NOT NULL " +
                "  GROUP BY content_hash HAVING COUNT(*) > 1" +
                ") sub", Long.class);
        stats.put("duplicateAttachments", duplicates != null ? duplicates : 0L);

        return ResponseEntity.ok(stats);
    }
}
