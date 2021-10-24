package com.zesty.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication()
@ComponentScan(basePackages = {
        "com.zesty.project.config",
        "com.zesty.project.repositories",
        "com.zesty.project.services",
})
public class ZestyApp {
    public static void main(String[] args) {
        SpringApplication.run(ZestyApp.class, args);
    }
}