package com.social.app.controller.rest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import jakarta.annotation.PostConstruct;
import java.net.URI;

/**
 * Proxies media files from MinIO/S3 storage.
 * In production, replace this with a CDN or direct S3 access.
 */
@RestController
@RequestMapping("/api/media")
public class MediaController {

    @Value("${social.media.s3.endpoint:http://localhost:9000}")
    private String endpoint;
    @Value("${social.media.s3.access-key:admin}")
    private String accessKey;
    @Value("${social.media.s3.secret-key:password123}")
    private String secretKey;
    @Value("${social.media.s3.bucket:worksphere}")
    private String bucket;
    @Value("${social.media.s3.region:us-east-1}")
    private String region;

    private S3Client s3;

    @PostConstruct
    public void init() {
        s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .build();
    }

    @GetMapping("/{filename}")
    public ResponseEntity<byte[]> getMedia(@PathVariable String filename) {
        try {
            var response = s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(filename)
                    .build());

            String contentType = response.response().contentType();
            if (contentType == null) contentType = "application/octet-stream";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                    .body(response.asByteArray());
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
