package net.ftgo.kitchen;

import net.ftgo.common.messaging.IdempotentCommandConfiguration;
import net.ftgo.common.messaging.OutboxMetricsConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Kitchen Service entry point. */
@SpringBootApplication
@EnableScheduling
@Import({IdempotentCommandConfiguration.class, OutboxMetricsConfiguration.class})
public class KitchenServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(KitchenServiceApplication.class, args);
    }
}
