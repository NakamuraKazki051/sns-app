package com.snsapp.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SnsApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(SnsApiApplication.class, args);
    }
}
