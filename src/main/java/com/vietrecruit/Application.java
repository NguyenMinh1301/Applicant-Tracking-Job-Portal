package com.vietrecruit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class Application {

    public static void main(String[] args) {

        System.setProperty("user.timezone", "Asia/Ho_Chi_Minh");

        SpringApplication.run(Application.class, args);
    }
}
