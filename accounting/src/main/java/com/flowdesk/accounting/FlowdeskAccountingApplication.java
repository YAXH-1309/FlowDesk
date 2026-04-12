package com.flowdesk.accounting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.flowdesk.accounting", "com.flowdesk.core"})
public class FlowdeskAccountingApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlowdeskAccountingApplication.class, args);
    }
}
