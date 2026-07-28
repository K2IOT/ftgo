package net.ftgo.order.api;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.LocalDateTime;

public final class WithinSchedulingWindowValidator
    implements ConstraintValidator<WithinSchedulingWindow, LocalDateTime> {

    @Value("${ftgo.order.max-scheduling-window:PT168H}")
    private String configuredWindow = "PT168H";

    @Override
    public boolean isValid(LocalDateTime value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        Duration maximum = Duration.parse(configuredWindow);
        return !value.isAfter(LocalDateTime.now().plus(maximum));
    }
}
