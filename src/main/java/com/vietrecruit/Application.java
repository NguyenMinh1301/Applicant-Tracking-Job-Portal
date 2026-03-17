package com.vietrecruit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.vietrecruit.feature.ai.shared.config.AiProperties;
import com.vietrecruit.feature.ai.shared.config.VectorStoreProperties;

@EnableAsync
@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties({AiProperties.class, VectorStoreProperties.class})
public class Application {

    public static void main(String[] args) {

        System.setProperty("user.timezone", "Asia/Ho_Chi_Minh");

        SpringApplication.run(Application.class, args);
    }
}
