package com.social.app.controller.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/link-preview")
public class LinkPreviewController {

    private static final Pattern OG_PATTERN = Pattern.compile(
            "<meta\\s+(?:property|name)=[\"'](og:[^\"']+)[\"']\\s+content=[\"']([^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_PATTERN_REV = Pattern.compile(
            "<meta\\s+content=[\"']([^\"']*)[\"']\\s+(?:property|name)=[\"'](og:[^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "<title[^>]*>([^<]+)</title>",
            Pattern.CASE_INSENSITIVE);

    @GetMapping
    public ResponseEntity<Map<String, String>> preview(@RequestParam("url") String url) {
        try {
            // Validate URL
            URI uri = URI.create(url);
            if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
                return ResponseEntity.badRequest().build();
            }

            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; SocialBot/1.0)");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setInstanceFollowRedirects(true);

            int status = conn.getResponseCode();
            if (status != 200) {
                return ResponseEntity.ok(Map.of());
            }

            // Read first 50KB of response
            StringBuilder html = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                char[] buffer = new char[8192];
                int read;
                int total = 0;
                while ((read = reader.read(buffer)) != -1 && total < 50000) {
                    html.append(buffer, 0, read);
                    total += read;
                }
            }

            String body = html.toString();
            Map<String, String> meta = new HashMap<>();
            meta.put("url", url);

            // Extract OG tags
            Matcher matcher = OG_PATTERN.matcher(body);
            while (matcher.find()) {
                String property = matcher.group(1).toLowerCase();
                String content = matcher.group(2);
                switch (property) {
                    case "og:title" -> meta.put("title", content);
                    case "og:description" -> meta.put("description", content);
                    case "og:image" -> meta.put("image", content);
                    case "og:site_name" -> meta.put("siteName", content);
                    case "og:type" -> meta.put("type", content);
                }
            }

            // Also try reversed attribute order
            matcher = OG_PATTERN_REV.matcher(body);
            while (matcher.find()) {
                String content = matcher.group(1);
                String property = matcher.group(2).toLowerCase();
                switch (property) {
                    case "og:title" -> meta.putIfAbsent("title", content);
                    case "og:description" -> meta.putIfAbsent("description", content);
                    case "og:image" -> meta.putIfAbsent("image", content);
                    case "og:site_name" -> meta.putIfAbsent("siteName", content);
                }
            }

            // Fallback to <title> tag
            if (!meta.containsKey("title")) {
                Matcher titleMatcher = TITLE_PATTERN.matcher(body);
                if (titleMatcher.find()) {
                    meta.put("title", titleMatcher.group(1).trim());
                }
            }

            return ResponseEntity.ok(meta);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of());
        }
    }
}
