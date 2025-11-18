package com.BossLiftingClub.BossLifting;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // CORS is handled by SecurityConfig, so we can remove this to avoid conflicts
        // Keeping it minimal or removing entirely since SecurityConfig handles CORS
    }
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/passes/**")
                .addResourceLocations("file:temp/passes/");

        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(0);

        // Explicitly disable resource handling for /users/**
        registry.addResourceHandler("/users/**")
                .resourceChain(false); // Disables resource handling for this path
    }
}