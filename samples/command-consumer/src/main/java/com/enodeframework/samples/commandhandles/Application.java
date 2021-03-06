package com.enodeframework.samples.commandhandles;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.enodeframework")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}