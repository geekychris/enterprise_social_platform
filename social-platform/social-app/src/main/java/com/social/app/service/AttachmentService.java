package com.social.app.service;

import com.social.app.persistence.entity.AttachmentEntity;
import com.social.app.persistence.repository.AttachmentRepository;
import com.social.core.dto.AttachmentDto;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;

@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private final AttachmentRepository attachmentRepository;
    private final GlobalIdGenerator idGenerator;
    private final S3Client s3Client;
    private final String bucket;

    public AttachmentService(AttachmentRepository attachmentRepository,
                             GlobalIdGenerator idGenerator,
                             @Value("${social.media.s3.endpoint:http://localhost:9000}") String endpoint,
                             @Value("${social.media.s3.access-key:admin}") String accessKey,
                             @Value("${social.media.s3.secret-key:password123}") String secretKey,
                             @Value("${social.media.s3.bucket:worksphere}") String bucket,
                             @Value("${social.media.s3.region:us-east-1}") String region) {
        this.attachmentRepository = attachmentRepository;
        this.idGenerator = idGenerator;
        this.bucket = bucket;
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .build();
        log.info("AttachmentService initialized with S3 endpoint: {}, bucket: {}", endpoint, bucket);
    }

    @Transactional
    public AttachmentEntity upload(long ownerId, MultipartFile file) throws IOException {
        // Compute content hash
        byte[] fileBytes = file.getBytes();
        String contentHash = computeSha256(fileBytes);

        // Check for existing attachment with same hash
        Optional<AttachmentEntity> existing = attachmentRepository.findByContentHash(contentHash);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Generate stored filename
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String storedFilename = UUID.randomUUID() + extension;

        // Upload to S3/MinIO
        String s3Key = storedFilename;
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(s3Key)
                        .contentType(file.getContentType())
                        .build(),
                RequestBody.fromBytes(fileBytes));
        log.debug("Uploaded file to S3: {}/{}", bucket, s3Key);

        var entity = new AttachmentEntity();
        entity.setId(idGenerator.next(ObjectType.ATTACHMENT).value());
        entity.setOwnerId(ownerId);
        entity.setMediaType(file.getContentType());
        entity.setFileUrl("/api/media/" + storedFilename);
        entity.setFileSize(file.getSize());
        entity.setContentHash(contentHash);

        return attachmentRepository.save(entity);
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public Optional<AttachmentEntity> getById(long id) {
        return attachmentRepository.findById(id);
    }

    public AttachmentDto toDto(AttachmentEntity entity) {
        return new AttachmentDto(
                entity.getId(),
                entity.getFileUrl(),
                entity.getMediaType(),
                entity.getFileSize(),
                entity.getWidth(),
                entity.getHeight()
        );
    }
}
