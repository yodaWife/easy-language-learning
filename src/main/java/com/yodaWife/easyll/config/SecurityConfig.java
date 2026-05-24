package com.yodawife.easyll.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configures the Spring Security filter chain.
 *
 * <p>Gameplay endpoints are public; {@code /admin/**} requires {@code ROLE_ADMIN}.
 * CSRF is disabled so that HTMX POST requests work without a CSRF token.
 * Form login is disabled so that unauthenticated requests to {@code /admin/**}
 * receive a 401 response rather than a redirect to a login page.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/session/start",
                                "/flashcards", "/flashcards/card",
                                "/match", "/match/attempt", "/match/result",
                                "/health/data",
                                "/dictionary", "/dictionary/**",
                                "/css/**", "/webjars/**"
                        ).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
