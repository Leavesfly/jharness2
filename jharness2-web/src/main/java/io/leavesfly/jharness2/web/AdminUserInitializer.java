package io.leavesfly.jharness2.web;

import io.leavesfly.jharness2.storage.entity.UserEntity;
import io.leavesfly.jharness2.storage.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 首次启动时初始化管理员账号。
 * <p>
 * 密码优先取 {@code jharness2.security.admin-password}（建议由环境变量 ADMIN_PASSWORD 注入）；
 * 未配置时生成一次性随机密码并打印到启动日志，避免固定的默认口令随代码库公开。
 */
@Component
@Order(1)
public class AdminUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);
    private static final int GENERATED_PASSWORD_BYTES = 18;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String configuredPassword;

    public AdminUserInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
                               @Value("${jharness2.security.admin-password:}") String configuredPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.configuredPassword = configuredPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername("admin")) {
            return;
        }
        boolean generated = configuredPassword == null || configuredPassword.isBlank();
        String password = generated ? generatePassword() : configuredPassword;

        java.time.Instant now = java.time.Instant.now();
        UserEntity admin = new UserEntity();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setDisplayName("Administrator");
        admin.setRole("ADMIN");
        admin.setEnabled(true);
        admin.setOnboardingCompleted(true);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        userRepository.save(admin);

        if (generated) {
            log.warn("已创建管理员账号 admin，一次性随机密码: {}（仅本次打印，请立即登录并修改）", password);
        } else {
            log.info("已创建管理员账号 admin（密码取自 jharness2.security.admin-password）");
        }
    }

    private static String generatePassword() {
        byte[] bytes = new byte[GENERATED_PASSWORD_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
