package com.flowdesk.task;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.flowdesk.task", "com.flowdesk.core"})
public class FlowdeskTaskApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlowdeskTaskApplication.class, args);
    }
}
