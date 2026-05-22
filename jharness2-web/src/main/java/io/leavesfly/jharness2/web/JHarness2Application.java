package io.leavesfly.jharness2.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

@SpringBootApplication(scanBasePackages = {
        "io.leavesfly.jharness2.core",
        "io.leavesfly.jharness2.storage",
        "io.leavesfly.jharness2.web"
})
@EnableJdbcRepositories(basePackages = "io.leavesfly.jharness2.storage.repository")
@EnableConfigurationProperties
public class JHarness2Application {

    public static void main(String[] args) {
        SpringApplication.run(JHarness2Application.class, args);
    }
}
