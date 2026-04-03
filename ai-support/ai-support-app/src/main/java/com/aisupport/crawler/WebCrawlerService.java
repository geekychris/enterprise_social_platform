package com.aisupport.crawler;

import com.aisupport.persistence.entity.CrawlJobEntity;
import com.aisupport.persistence.repository.CrawlJobRepository;
import com.aisupport.service.KnowledgeService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class WebCrawlerService {
    private static final Logger log = LoggerFactory.getLogger(WebCrawlerService.class);

    private final CrawlJobRepository crawlJobRepository;
    private final KnowledgeService knowledgeService;

    @Value("${aisupport.crawler.max-depth:3}")
    private int defaultMaxDepth;
    @Value("${aisupport.crawler.max-pages:100}")
    private int defaultMaxPages;
    @Value("${aisupport.crawler.delay-ms:1000}")
    private long delayMs;
    @Value("${aisupport.crawler.user-agent:WorkSphere-AI-Support-Crawler/1.0}")
    private String userAgent;

    public WebCrawlerService(CrawlJobRepository crawlJobRepository, KnowledgeService knowledgeService) {
        this.crawlJobRepository = crawlJobRepository;
        this.knowledgeService = knowledgeService;
    }

    @Transactional
    public CrawlJobEntity createJob(long knowledgeSetId, String startUrl, Integer maxDepth, Integer maxPages) {
        var job = new CrawlJobEntity();
        job.setKnowledgeSetId(knowledgeSetId);
        job.setStartUrl(startUrl);
        job.setMaxDepth(maxDepth != null ? maxDepth : defaultMaxDepth);
        job.setMaxPages(maxPages != null ? maxPages : defaultMaxPages);
        job.setStatus("PENDING");
        return crawlJobRepository.save(job);
    }

    @Async
    public void executeCrawl(long jobId) {
        var job = crawlJobRepository.findById(jobId).orElse(null);
        if (job == null) return;

        job.setStatus("RUNNING");
        job.setStartedAt(OffsetDateTime.now());
        crawlJobRepository.save(job);

        Set<String> visited = new HashSet<>();
        Queue<CrawlTask> queue = new LinkedList<>();
        queue.add(new CrawlTask(job.getStartUrl(), 0));

        String baseDomain;
        try {
            baseDomain = new URI(job.getStartUrl()).getHost();
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setErrorMessage("Invalid start URL: " + e.getMessage());
            job.setCompletedAt(OffsetDateTime.now());
            crawlJobRepository.save(job);
            return;
        }

        int crawled = 0;
        int indexed = 0;

        try {
            while (!queue.isEmpty() && crawled < job.getMaxPages()) {
                var task = queue.poll();
                if (task.depth > job.getMaxDepth()) continue;
                if (!visited.add(task.url)) continue;

                try {
                    Document doc = Jsoup.connect(task.url)
                            .userAgent(userAgent)
                            .timeout(10000)
                            .get();

                    String title = doc.title();
                    String text = doc.body().text();

                    // Skip very short pages
                    if (text.length() < 100) continue;

                    // Add as document
                    var docEntity = knowledgeService.addDocument(
                            job.getKnowledgeSetId(),
                            title.isEmpty() ? task.url : title,
                            text,
                            task.url,
                            "CRAWL"
                    );
                    crawled++;

                    // Index immediately
                    knowledgeService.indexDocument(docEntity.getId());
                    indexed++;

                    // Extract links for further crawling
                    if (task.depth < job.getMaxDepth()) {
                        var links = doc.select("a[href]");
                        for (var link : links) {
                            String href = link.absUrl("href");
                            if (href.isEmpty()) continue;
                            try {
                                var uri = new URI(href);
                                // Stay on same domain
                                if (uri.getHost() != null && uri.getHost().equals(baseDomain)
                                        && !visited.contains(href)
                                        && !href.contains("#")
                                        && (href.startsWith("http://") || href.startsWith("https://"))) {
                                    queue.add(new CrawlTask(href, task.depth + 1));
                                }
                            } catch (Exception ignored) {}
                        }
                    }

                    // Be polite
                    Thread.sleep(delayMs);

                } catch (Exception e) {
                    log.debug("Failed to crawl {}: {}", task.url, e.getMessage());
                }

                // Update progress
                job.setPagesCrawled(crawled);
                job.setPagesIndexed(indexed);
                crawlJobRepository.save(job);
            }

            job.setStatus("COMPLETED");
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
        }

        job.setPagesCrawled(crawled);
        job.setPagesIndexed(indexed);
        job.setCompletedAt(OffsetDateTime.now());
        crawlJobRepository.save(job);
        log.info("Crawl job {} completed: crawled={}, indexed={}", jobId, crawled, indexed);
    }

    private record CrawlTask(String url, int depth) {}
}
