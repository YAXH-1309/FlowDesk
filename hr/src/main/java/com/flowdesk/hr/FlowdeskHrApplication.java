package com.flowdesk.hr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.flowdesk.hr", "com.flowdesk.core"})
public class FlowdeskHrApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlowdeskHrApplication.class, args);
    }
}
