package io.leavesfly.jharness2.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "io.leavesfly.jharness2.core",
        "io.leavesfly.jharness2.storage",
        "io.leavesfly.jharness2.web"
})
@EntityScan(basePackages = "io.leavesfly.jharness2.storage.entity")
@EnableJpaRepositories(basePackages = "io.leavesfly.jharness2.storage.repository")
@EnableConfigurationProperties
public class JHarness2Application {

    public static void main(String[] args) {
        SpringApplication.run(JHarness2Application.class, args);
    }
}
