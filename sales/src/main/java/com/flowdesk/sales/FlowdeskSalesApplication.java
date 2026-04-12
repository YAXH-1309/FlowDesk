package com.flowdesk.sales;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@ComponentScan(basePackages = {"com.flowdesk.sales", "com.flowdesk.core"})
public class FlowdeskSalesApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlowdeskSalesApplication.class, args);
    }
}
