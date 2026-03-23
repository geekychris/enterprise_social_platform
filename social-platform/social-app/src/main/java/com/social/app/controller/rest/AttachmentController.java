package com.social.app.controller.rest;

import com.social.app.service.AttachmentService;
import com.social.core.dto.AttachmentDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping("/upload")
    public ResponseEntity<AttachmentDto> upload(@RequestParam("file") MultipartFile file,
                                                Authentication auth) throws IOException {
        long userId = (Long) auth.getPrincipal();
        var entity = attachmentService.upload(userId, file);
        return ResponseEntity.ok(attachmentService.toDto(entity));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AttachmentDto> getAttachment(@PathVariable long id) {
        return attachmentService.getById(id)
                .map(attachmentService::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
