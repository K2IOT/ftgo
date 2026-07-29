package net.ftgo.common.observability;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CorrelationContextFilter.class)
    CorrelationContextFilter correlationContextFilter() {
        return new CorrelationContextFilter();
    }

    @Bean
    FilterRegistrationBean<CorrelationContextFilter> correlationContextFilterRegistration(
        CorrelationContextFilter filter
    ) {
        FilterRegistrationBean<CorrelationContextFilter> registration =
            new FilterRegistrationBean<>(filter);
        registration.setName("ftgoCorrelationContextFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
