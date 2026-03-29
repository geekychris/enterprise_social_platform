package com.social.app.controller.rest;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.*;

/**
 * Exposes recent analytics events from Kafka for viewing in the admin UI.
 */
@RestController
@RequestMapping("/api/admin/analytics")
public class AnalyticsViewController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsViewController.class);

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String kafkaServers;

    /**
     * Read recent events from a Kafka topic.
     */
    @GetMapping("/events/{topic}")
    public ResponseEntity<Map<String, Object>> getEvents(
            @PathVariable String topic,
            @RequestParam(defaultValue = "50") int limit) {

        // Whitelist topics
        Set<String> allowed = Set.of(
                "worksphere-feed-impressions",
                "worksphere-user-interactions",
                "posts.created",
                "messages.sent",
                "reactions.added"
        );
        if (!allowed.contains(topic)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Topic not allowed: " + topic));
        }

        List<String> events = new ArrayList<>();
        try {
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaServers);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "analytics-viewer-" + System.currentTimeMillis());
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
            props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, String.valueOf(limit));

            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
                consumer.subscribe(List.of(topic));

                // First poll to get assignment, then seek to recent
                consumer.poll(Duration.ofMillis(500));
                var partitions = consumer.assignment();
                var endOffsets = consumer.endOffsets(partitions);

                // Seek to max(0, end - limit) for each partition
                for (var tp : partitions) {
                    long end = endOffsets.getOrDefault(tp, 0L);
                    long start = Math.max(0, end - limit / Math.max(partitions.size(), 1));
                    consumer.seek(tp, start);
                }

                // Poll for events
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(2000));
                records.forEach(record -> {
                    if (events.size() < limit) {
                        events.add(record.value());
                    }
                });
            }
        } catch (Exception e) {
            log.warn("Failed to read Kafka topic {}: {}", topic, e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "topic", topic,
                    "error", e.getMessage(),
                    "events", List.of()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "topic", topic,
                "count", events.size(),
                "events", events
        ));
    }

    /**
     * List available analytics topics with message counts.
     */
    @GetMapping("/topics")
    public ResponseEntity<List<Map<String, Object>>> getTopics() {
        List<Map<String, Object>> topics = new ArrayList<>();

        try {
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaServers);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "topic-viewer-" + System.currentTimeMillis());
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
                var topicList = consumer.listTopics();
                for (String name : List.of("worksphere-feed-impressions", "worksphere-user-interactions",
                        "posts.created", "messages.sent", "reactions.added")) {
                    if (topicList.containsKey(name)) {
                        var partitions = topicList.get(name).stream()
                                .map(pi -> new org.apache.kafka.common.TopicPartition(name, pi.partition()))
                                .toList();
                        var endOffsets = consumer.endOffsets(partitions);
                        long total = endOffsets.values().stream().mapToLong(Long::longValue).sum();
                        topics.add(Map.of("topic", name, "messageCount", total, "partitions", partitions.size()));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list topics: {}", e.getMessage());
        }

        return ResponseEntity.ok(topics);
    }
}
