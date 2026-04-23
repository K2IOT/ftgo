package net.ftgo.kitchen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for Kitchen Service.
 * 
 * Kitchen Service manages kitchen tickets (kitchen-perspective view of orders)
 * and participates in sagas orchestrated by the Order Service.
 * 
 * Key responsibilities:
 * - Create and manage kitchen tickets
 * - Participate in CreateOrderSaga, CancelOrderSaga, and ReviseOrderSaga
 * - Provide REST API for kitchen staff to accept and manage tickets
 * - Publish ticket lifecycle events via Transactional Outbox pattern
 */
@SpringBootApplication
public class KitchenServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(KitchenServiceApplication.class, args);
    }
}
