package com.mmo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String rootDir = System.getProperty("user.dir");
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + rootDir + "/uploads/");
    }
}
