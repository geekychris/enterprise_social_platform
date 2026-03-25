package com.social.app.controller.rest;

import com.social.app.service.InviteService;
import com.social.core.dto.AuthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/invite")
public class InviteController {

    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @GetMapping("/validate/{token}")
    public ResponseEntity<Map<String, Object>> validateToken(@PathVariable String token) {
        return ResponseEntity.ok(inviteService.validateToken(token));
    }

    @PostMapping("/setup/{token}")
    public ResponseEntity<?> setup(@PathVariable String token, @RequestBody SetupRequest request) {
        try {
            AuthResponse response = inviteService.redeemToken(
                    token, request.username(), request.password(), request.bio());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    record SetupRequest(String username, String password, String bio) {}
}
