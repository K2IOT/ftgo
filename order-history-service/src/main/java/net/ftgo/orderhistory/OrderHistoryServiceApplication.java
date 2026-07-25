package net.ftgo.orderhistory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OrderHistoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderHistoryServiceApplication.class, args);
    }
}
