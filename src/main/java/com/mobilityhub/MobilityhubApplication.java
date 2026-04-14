package com.mobilityhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties
public class MobilityhubApplication {
    public static void main(String[] args) {
        SpringApplication.run(MobilityhubApplication.class, args);
    }
}