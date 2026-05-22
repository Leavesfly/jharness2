package io.leavesfly.jharness2.web;

import io.leavesfly.jharness2.storage.entity.UserEntity;
import io.leavesfly.jharness2.storage.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class AdminUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername("admin")) {
            return;
        }
        java.time.Instant now = java.time.Instant.now();
        UserEntity admin = new UserEntity();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setDisplayName("Administrator");
        admin.setRole("ADMIN");
        admin.setEnabled(true);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        userRepository.save(admin);
        log.info("Default admin user created (admin / admin123)");
    }
}
