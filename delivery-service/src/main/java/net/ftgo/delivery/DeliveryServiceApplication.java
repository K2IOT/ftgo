package net.ftgo.delivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for Delivery Service.
 * 
 * Manages courier assignment and delivery tracking for approved orders.
 */
@SpringBootApplication
public class DeliveryServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(DeliveryServiceApplication.class, args);
    }
}
