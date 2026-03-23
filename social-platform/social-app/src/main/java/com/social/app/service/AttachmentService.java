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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;

@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private final AttachmentRepository attachmentRepository;
    private final GlobalIdGenerator idGenerator;
    private final Path uploadDir;

    public AttachmentService(AttachmentRepository attachmentRepository,
                             GlobalIdGenerator idGenerator,
                             @Value("${social.media.upload-dir}") String uploadDir) {
        this.attachmentRepository = attachmentRepository;
        this.idGenerator = idGenerator;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            log.warn("Could not create upload directory: {}", this.uploadDir, e);
        }
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

        // Save file
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String storedFilename = UUID.randomUUID() + extension;
        Path targetPath = uploadDir.resolve(storedFilename);
        Files.write(targetPath, fileBytes);

        var entity = new AttachmentEntity();
        entity.setId(idGenerator.next(ObjectType.ATTACHMENT).value());
        entity.setOwnerId(ownerId);
        entity.setMediaType(file.getContentType());
        entity.setFileUrl("/uploads/" + storedFilename);
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
