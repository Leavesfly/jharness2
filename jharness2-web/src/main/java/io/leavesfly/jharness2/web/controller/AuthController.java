package io.leavesfly.jharness2.web.controller;

import io.leavesfly.jharness2.storage.entity.UserEntity;
import io.leavesfly.jharness2.storage.repository.UserRepository;
import io.leavesfly.jharness2.web.dto.LoginRequest;
import io.leavesfly.jharness2.web.dto.LoginResponse;
import io.leavesfly.jharness2.web.dto.OnboardingRequest;
import io.leavesfly.jharness2.web.dto.RegisterRequest;
import io.leavesfly.jharness2.web.security.JwtTokenProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                         JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        UserEntity user = userRepository.findByUsername(request.getUsername())
                .orElse(null);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }
        if (!user.isEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Account is disabled"));
        }
        String token = tokenProvider.generateToken(user.getUsername(), user.getRole());
        return ResponseEntity.ok(new LoginResponse(token, user.getUsername(),
                tokenProvider.getExpirationMs() / 1000, user.isOnboardingCompleted()));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Username already exists"));
        }
        Instant now = Instant.now();
        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole("USER");
        user.setEnabled(true);
        user.setOnboardingCompleted(false);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

        String token = tokenProvider.generateToken(user.getUsername(), user.getRole());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new LoginResponse(token, user.getUsername(),
                        tokenProvider.getExpirationMs() / 1000, false));
    }

    @PostMapping("/onboarding")
    public ResponseEntity<?> completeOnboarding(@Valid @RequestBody OnboardingRequest request,
                                                Authentication authentication) {
        String username = authentication.getName();
        UserEntity user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found"));
        }
        if (user.isOnboardingCompleted()) {
            return ResponseEntity.ok(Map.of("message", "Onboarding already completed"));
        }

        user.setApiKey(request.getApiKey());
        if (request.getBaseUrl() != null && !request.getBaseUrl().isBlank()) {
            user.setBaseUrl(request.getBaseUrl());
        }
        if (request.getPreferredModel() != null && !request.getPreferredModel().isBlank()) {
            user.setPreferredModel(request.getPreferredModel());
        }
        if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
            user.setDisplayName(request.getDisplayName());
        }
        user.setOnboardingCompleted(true);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Onboarding completed successfully",
                "onboardingCompleted", true));
    }
}
