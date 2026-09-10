package com.paypus.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.paypus.tenant.ApiKeyAuthFilter;
import com.paypus.tenant.ApiKeyRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ApiKeyRepository apiKeyRepository) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/v1/webhooks/**").permitAll().anyRequest().authenticated())
                .addFilterBefore(
                        new ApiKeyAuthFilter(apiKeyRepository),
                        UsernamePasswordAuthenticationFilter.class
                );
        return http.build();
    }
}
