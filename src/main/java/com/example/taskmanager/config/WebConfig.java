package com.example.taskmanager.config;

import com.example.taskmanager.entity.TaskPriority;
import com.example.taskmanager.entity.TaskStatus;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Lets query parameters use the API representation (?status=in_progress&priority=high)
     * instead of the Java enum names (IN_PROGRESS).
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, TaskStatus.class, TaskStatus::fromValue);
        registry.addConverter(String.class, TaskPriority.class, TaskPriority::fromValue);
    }
}
