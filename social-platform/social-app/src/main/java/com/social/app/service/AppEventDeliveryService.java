package com.social.app.service;

import com.social.app.persistence.entity.AppEntity;
import com.social.app.persistence.entity.AppEventEntity;
import com.social.app.persistence.repository.AppEventRepository;
import com.social.app.persistence.repository.AppRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Delivers app events to webhook URLs.
 *
 * Uses a notify pattern: when AppService queues an event, it calls notifyNewEvent()
 * which immediately wakes the delivery thread. No polling delay.
 *
 * Also runs a background sweep every 30 seconds for retries and any missed events.
 */
@Service
public class AppEventDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(AppEventDeliveryService.class);
    private static final int MAX_RETRIES = 5;
    private static final Duration[] BACKOFF = {
            Duration.ofSeconds(10),
            Duration.ofSeconds(30),
            Duration.ofMinutes(2),
            Duration.ofMinutes(10),
            Duration.ofMinutes(30)
    };

    private final AppEventRepository eventRepository;
    private final AppRepository appRepository;
    private final RestTemplate restTemplate;

    // Notification queue — when an event is queued, a signal is sent here
    private final LinkedBlockingQueue<Long> deliverySignal = new LinkedBlockingQueue<>();
    private final ExecutorService deliveryExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "app-event-delivery");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean running = true;

    public AppEventDeliveryService(AppEventRepository eventRepository,
                                    AppRepository appRepository) {
        this.eventRepository = eventRepository;
        this.appRepository = appRepository;
        this.restTemplate = new RestTemplate();
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(10_000);
        this.restTemplate.setRequestFactory(factory);
    }

    @PostConstruct
    public void start() {
        deliveryExecutor.submit(this::deliveryLoop);
        log.info("App event delivery service started (notify + 30s sweep)");
    }

    @PreDestroy
    public void stop() {
        running = false;
        deliverySignal.offer(0L); // Wake up the loop so it can exit
        deliveryExecutor.shutdown();
        try { deliveryExecutor.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
    }

    /**
     * Called by AppService after queuing an event. Wakes the delivery thread immediately.
     */
    public void notifyNewEvent(long appId) {
        deliverySignal.offer(appId);
    }

    /**
     * Main delivery loop. Waits for signals or times out every 30 seconds for sweep.
     */
    private void deliveryLoop() {
        while (running) {
            try {
                // Wait for signal or timeout (30s sweep for retries)
                Long signalAppId = deliverySignal.poll(30, TimeUnit.SECONDS);

                // Drain any additional signals (batch multiple rapid events)
                deliverySignal.clear();

                // Deliver all pending events
                deliverPendingEvents();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Delivery loop error: {}", e.getMessage());
                try { Thread.sleep(1000); } catch (InterruptedException ignored) { break; }
            }
        }
        log.info("App event delivery service stopped");
    }

    @Transactional
    public void deliverPendingEvents() {
        Instant now = Instant.now();
        List<AppEntity> apps = appRepository.findByActive(true);

        for (AppEntity app : apps) {
            try {
                deliverEventsForApp(app, now);
            } catch (Exception e) {
                log.warn("Error delivering events for app {}: {}", app.getId(), e.getMessage());
            }
        }
    }

    private void deliverEventsForApp(AppEntity app, Instant now) {
        List<AppEventEntity> pendingEvents = eventRepository.findByAppIdAndStatus(app.getId(), "PENDING");
        List<AppEventEntity> retryEvents = eventRepository.findByAppIdAndStatusAndNextRetryAtBefore(app.getId(), "FAILED", now);

        List<AppEventEntity> toDeliver = new java.util.ArrayList<>(pendingEvents);
        toDeliver.addAll(retryEvents);

        for (AppEventEntity event : toDeliver) {
            if (event.getNextRetryAt() != null && event.getNextRetryAt().isAfter(now)) {
                continue;
            }

            try {
                deliverEvent(app, event);
                event.setStatus("DELIVERED");
                event.setDeliveredAt(Instant.now());
                eventRepository.save(event);
                log.debug("Delivered event {} to app {}", event.getId(), app.getName());
            } catch (Exception e) {
                int retryCount = event.getRetryCount() + 1;
                event.setRetryCount(retryCount);
                event.setErrorMessage(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : "Unknown error");

                if (retryCount >= MAX_RETRIES) {
                    event.setStatus("DEAD");
                    log.warn("Event {} marked as DEAD after {} retries for app {}", event.getId(), retryCount, app.getId());
                } else {
                    event.setStatus("FAILED");
                    Duration backoff = retryCount <= BACKOFF.length ? BACKOFF[retryCount - 1] : BACKOFF[BACKOFF.length - 1];
                    event.setNextRetryAt(Instant.now().plus(backoff));
                }
                eventRepository.save(event);
            }
        }
    }

    private void deliverEvent(AppEntity app, AppEventEntity event) {
        String envelope = String.format(
            "{\"event\":\"%s\",\"timestamp\":\"%s\",\"installation\":{\"id\":%s,\"type\":\"PAGE\",\"targetId\":0},\"data\":{\"post\":%s}}",
            event.getEventType(),
            event.getCreatedAt() != null ? event.getCreatedAt().toString() : Instant.now().toString(),
            event.getInstallationId() != null ? event.getInstallationId().toString() : "0",
            event.getPayload()
        );

        String signature = computeHmacSignature(envelope, app.getApiKeyHash());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-WorkSphere-Signature", signature);
        headers.set("X-WorkSphere-Event", event.getEventType());
        headers.set("X-WorkSphere-App-Id", String.valueOf(app.getId()));
        headers.set("X-WorkSphere-Event-Id", String.valueOf(event.getId()));

        HttpEntity<String> request = new HttpEntity<>(envelope, headers);
        restTemplate.postForEntity(app.getWebhookUrl(), request, String.class);
    }

    private String computeHmacSignature(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(hmac);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC signature", e);
        }
    }
}
