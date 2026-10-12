package com.example.taskmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// UserDetailsServiceAutoConfiguration is excluded: authentication is done with JWTs,
// so Spring Boot's default in-memory user (with a generated password) is not wanted.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class TaskManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskManagerApplication.class, args);
    }
}
