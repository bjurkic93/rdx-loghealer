package com.reddiax.loghealer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableJpaRepositories(basePackages = "com.reddiax.loghealer.repository.jpa")
@EnableElasticsearchRepositories(basePackages = "com.reddiax.loghealer.repository.elasticsearch")
public class RdxLogHealerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RdxLogHealerApplication.class, args);
    }
}
