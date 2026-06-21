package com.lab.rollout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RolloutApplication {
    public static void main(String[] args) {
        SpringApplication.run(RolloutApplication.class, args);
    }
}