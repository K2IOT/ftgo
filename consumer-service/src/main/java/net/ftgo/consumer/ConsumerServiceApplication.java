package net.ftgo.consumer;

import net.ftgo.common.messaging.IdempotentCommandConfiguration;
import net.ftgo.common.messaging.OutboxMetricsConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({IdempotentCommandConfiguration.class, OutboxMetricsConfiguration.class})
public class ConsumerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsumerServiceApplication.class, args);
    }
}
