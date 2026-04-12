package com.flowdesk.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.flowdesk.inventory", "com.flowdesk.core"})
public class FlowdeskInventoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlowdeskInventoryApplication.class, args);
    }
}
