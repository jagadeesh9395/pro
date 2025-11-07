package com.tal.pro.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class FileStorageConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.max-size:10485760}") // 10MB default
    private long maxFileSize;

    @Value("${file.allowed-extensions:.pdf,.doc,.docx}")
    private String[] allowedExtensions;

    @Bean
    public Path uploadPath() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Bean
    public long maxFileSize() {
        return maxFileSize;
    }

    @Bean
    public String[] allowedExtensions() {
        return allowedExtensions;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve uploaded files
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath() + "/")
                .setCachePeriod(3600)
                .resourceChain(true);
    }
}
