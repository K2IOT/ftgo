package net.ftgo.kitchen.config;

import net.ftgo.common.security.FtgoAuthorizationManagers;
import net.ftgo.common.security.FtgoJwtAuthenticationConverter;
import net.ftgo.common.security.FtgoJwtDecoders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        @Value("${ftgo.security.public-audience:ftgo-api}") String publicAudience,
        @Value("${ftgo.security.internal-audience:ftgo-internal}") String internalAudience
    ) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/actuator/health/liveness",
                    "/actuator/health/readiness"
                ).permitAll()
                .requestMatchers("/internal/**")
                    .access(FtgoAuthorizationManagers.internalService(internalAudience))
                .requestMatchers("/actuator/**").hasAnyRole("ADMIN", "SERVICE")
                .requestMatchers("/tickets/**")
                    .access(FtgoAuthorizationManagers.publicApi(
                        publicAudience,
                        "RESTAURANT",
                        "ADMIN"
                    ))
                .anyRequest().denyAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                jwt.jwtAuthenticationConverter(new FtgoJwtAuthenticationConverter(""))
            ))
            .build();
    }

    @Bean
    JwtDecoder jwtDecoder(
        @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
        @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
        @Value("${ftgo.security.public-audience:ftgo-api}") String publicAudience,
        @Value("${ftgo.security.internal-audience:ftgo-internal}") String internalAudience
    ) {
        return FtgoJwtDecoders.create(issuerUri, jwkSetUri, List.of(publicAudience, internalAudience));
    }
}
