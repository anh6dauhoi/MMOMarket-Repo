package com.mmo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MmoMarketApplication {
    public static void main(String[] args) {
        SpringApplication.run(MmoMarketApplication.class, args);
    }
}