package com.social.app.service;

import com.social.app.config.JwtUtil;
import com.social.app.persistence.entity.UserEntity;
import com.social.core.dto.AuthResponse;
import com.social.core.dto.CreateUserRequest;
import com.social.core.dto.LoginRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserService userService, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public AuthResponse register(CreateUserRequest request) {
        String hashedPassword = passwordEncoder.encode(request.password());
        UserEntity user = userService.create(
                request.username(),
                request.displayName(),
                request.email(),
                hashedPassword,
                request.bio()
        );
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new AuthResponse(token, user.getId(), user.getUsername(), user.isAdmin());
    }

    public AuthResponse login(LoginRequest request) {
        UserEntity user = userService.getByUsername(request.username())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new AuthResponse(token, user.getId(), user.getUsername(), user.isAdmin());
    }
}
