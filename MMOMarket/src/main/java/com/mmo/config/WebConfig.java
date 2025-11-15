package com.mmo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve files stored under project-root/uploads/** at URL /uploads/**
        Path uploadDir = Paths.get("uploads");
        String uploadPath = uploadDir.toFile().getAbsolutePath().replace("\\", "/") + "/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath)
                .setCachePeriod(3600);

        // Serve static resources from classpath
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/customer/css/**")
                .addResourceLocations("classpath:/static/customer/css/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/admin/css/**")
                .addResourceLocations("classpath:/static/admin/css/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/seller/css/**")
                .addResourceLocations("classpath:/static/seller/css/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/authen/css/**")
                .addResourceLocations("classpath:/static/authen/css/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/contracts/**")
                .addResourceLocations("classpath:/static/contracts/")
                .setCachePeriod(3600);
    }
}

