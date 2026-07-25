package net.ftgo.delivery;

import net.ftgo.common.messaging.OutboxMetricsConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** Main application class for Delivery Service. */
@SpringBootApplication
@Import(OutboxMetricsConfiguration.class)
public class DeliveryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeliveryServiceApplication.class, args);
    }
}
