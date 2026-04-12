package com.flowdesk.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.flowdesk.auth", "com.flowdesk.core"})
public class FlowdeskApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowdeskApplication.class, args);
    }
}
