package com.mmo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor t = new ThreadPoolTaskExecutor();
        t.setCorePoolSize(5);
        t.setMaxPoolSize(20);
        t.setQueueCapacity(200);
        t.setThreadNamePrefix("email-exec-");
        t.initialize();
        return t;
    }

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor t = new ThreadPoolTaskExecutor();
        t.setCorePoolSize(10);
        t.setMaxPoolSize(50);
        t.setQueueCapacity(500);
        t.setThreadNamePrefix("notif-exec-");
        t.initialize();
        return t;
    }

    @Bean(name = "fileUploadExecutor")
    public Executor fileUploadExecutor() {
        ThreadPoolTaskExecutor t = new ThreadPoolTaskExecutor();
        t.setCorePoolSize(3);
        t.setMaxPoolSize(10);
        t.setQueueCapacity(100);
        t.setThreadNamePrefix("file-upload-");
        t.initialize();
        return t;
    }
}

