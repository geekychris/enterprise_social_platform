package com.social.app.search;

import com.social.app.persistence.entity.GroupEntity;
import com.social.app.persistence.entity.PageEntity;
import com.social.app.persistence.entity.UserEntity;
import com.social.app.persistence.repository.GroupRepository;
import com.social.app.persistence.repository.PageRepository;
import com.social.app.persistence.repository.UserRepository;
import com.social.core.dto.SearchResultDto;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Search service with OpenSearch integration and DB LIKE query fallback.
 */
@Service
public class OpenSearchService {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchService.class);

    private final OpenSearchClient openSearchClient;
    private final UserRepository userRepository;
    private final PageRepository pageRepository;
    private final GroupRepository groupRepository;
    private final boolean openSearchAvailable;

    public OpenSearchService(OpenSearchClient openSearchClient,
                             UserRepository userRepository,
                             PageRepository pageRepository,
                             GroupRepository groupRepository) {
        this.openSearchClient = openSearchClient;
        this.userRepository = userRepository;
        this.pageRepository = pageRepository;
        this.groupRepository = groupRepository;
        this.openSearchAvailable = checkAvailability();
    }

    private boolean checkAvailability() {
        try {
            openSearchClient.info();
            log.info("OpenSearch is available");
            return true;
        } catch (Exception e) {
            log.warn("OpenSearch is not available, falling back to DB queries: {}", e.getMessage());
            return false;
        }
    }

    public void indexUser(UserEntity user) {
        if (!openSearchAvailable) return;
        try {
            openSearchClient.index(IndexRequest.of(i -> i
                    .index("users")
                    .id(String.valueOf(user.getId()))
                    .document(Map.of(
                            "username", user.getUsername(),
                            "displayName", user.getDisplayName() != null ? user.getDisplayName() : "",
                            "bio", user.getBio() != null ? user.getBio() : "",
                            "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : ""
                    ))
            ));
        } catch (IOException e) {
            log.warn("Failed to index user {}: {}", user.getId(), e.getMessage());
        }
    }

    public void indexPage(PageEntity page) {
        if (!openSearchAvailable) return;
        try {
            openSearchClient.index(IndexRequest.of(i -> i
                    .index("pages")
                    .id(String.valueOf(page.getId()))
                    .document(Map.of(
                            "name", page.getName(),
                            "description", page.getDescription() != null ? page.getDescription() : "",
                            "avatarUrl", page.getAvatarUrl() != null ? page.getAvatarUrl() : ""
                    ))
            ));
        } catch (IOException e) {
            log.warn("Failed to index page {}: {}", page.getId(), e.getMessage());
        }
    }

    public SearchResultDto search(String query, String type) {
        // OpenSearch only indexes users and pages — for other types, go straight to DB
        boolean canUseOpenSearch = openSearchAvailable &&
                (type == null || "user".equalsIgnoreCase(type) || "page".equalsIgnoreCase(type));
        if (canUseOpenSearch) {
            return searchOpenSearch(query, type);
        }
        return searchDatabase(query, type);
    }

    @SuppressWarnings("unchecked")
    private SearchResultDto searchOpenSearch(String query, String type) {
        try {
            List<String> indices = new ArrayList<>();
            if (type == null || "user".equalsIgnoreCase(type)) indices.add("users");
            if (type == null || "page".equalsIgnoreCase(type)) indices.add("pages");

            SearchRequest request = SearchRequest.of(s -> s
                    .index(indices)
                    .query(q -> q
                            .multiMatch(m -> m
                                    .query(query)
                                    .fields("username", "displayName", "name", "description", "bio")
                            )
                    )
                    .size(20)
            );

            SearchResponse<Map> response = openSearchClient.search(request, Map.class);

            List<SearchResultDto.SearchHit> hits = response.hits().hits().stream()
                    .map(hit -> {
                        Map<String, Object> source = hit.source();
                        String objectType = hit.index().equals("users") ? "USER" : "PAGE";
                        String name = source != null ?
                                (String) source.getOrDefault("displayName",
                                        source.getOrDefault("name", "")) : "";
                        String description = source != null ?
                                (String) source.getOrDefault("bio",
                                        source.getOrDefault("description", "")) : "";
                        String avatarUrl = source != null ?
                                (String) source.getOrDefault("avatarUrl", "") : "";
                        return new SearchResultDto.SearchHit(
                                Long.parseLong(hit.id()),
                                objectType,
                                name,
                                description,
                                avatarUrl,
                                hit.score() != null ? hit.score().floatValue() : 0f
                        );
                    })
                    .toList();

            long totalHits = response.hits().total() != null ? response.hits().total().value() : hits.size();
            return new SearchResultDto(hits, totalHits);
        } catch (Exception e) {
            log.warn("OpenSearch query failed, falling back to DB: {}", e.getMessage());
            return searchDatabase(query, type);
        }
    }

    private SearchResultDto searchDatabase(String query, String type) {
        List<SearchResultDto.SearchHit> hits = new ArrayList<>();

        if (type == null || "user".equalsIgnoreCase(type)) {
            List<UserEntity> users = userRepository.searchByUsernameOrDisplayName(query);
            for (UserEntity u : users) {
                // Build a rich description from profile fields
                StringBuilder desc = new StringBuilder();
                if (u.getJobTitle() != null) desc.append(u.getJobTitle());
                if (u.getDepartment() != null) {
                    if (desc.length() > 0) desc.append(" · ");
                    desc.append(u.getDepartment());
                }
                if (u.getLocation() != null) {
                    if (desc.length() > 0) desc.append(" · ");
                    desc.append(u.getLocation());
                }
                if (desc.length() == 0 && u.getBio() != null) {
                    desc.append(u.getBio());
                }
                hits.add(new SearchResultDto.SearchHit(
                        u.getId(), "USER",
                        u.getDisplayName() != null ? u.getDisplayName() : u.getUsername(),
                        desc.length() > 0 ? desc.toString() : null,
                        u.getAvatarUrl(),
                        1.0f
                ));
            }
        }

        if (type == null || "page".equalsIgnoreCase(type)) {
            List<PageEntity> pages = pageRepository.searchByName(query);
            for (PageEntity p : pages) {
                hits.add(new SearchResultDto.SearchHit(
                        p.getId(), "PAGE",
                        p.getName(),
                        p.getDescription(),
                        p.getAvatarUrl(),
                        1.0f
                ));
            }
        }

        if (type == null || "group".equalsIgnoreCase(type)) {
            List<GroupEntity> groups = groupRepository.searchByName(query);
            for (GroupEntity g : groups) {
                hits.add(new SearchResultDto.SearchHit(
                        g.getId(), "GROUP",
                        g.getName(),
                        g.getDescription(),
                        null,
                        1.0f
                ));
            }
        }

        return new SearchResultDto(hits, hits.size());
    }
}
