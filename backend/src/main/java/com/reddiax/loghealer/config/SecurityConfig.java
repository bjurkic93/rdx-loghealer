package com.reddiax.loghealer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/v1/health").permitAll()
                .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Log ingestion - API key auth (handled separately)
                .requestMatchers(HttpMethod.POST, "/api/v1/logs", "/api/v1/logs/batch").permitAll()
                // Event ingestion - API key auth
                .requestMatchers(HttpMethod.POST, "/api/v1/events/**").permitAll()
                // Protected endpoints - require authentication (role check temporarily disabled for debugging)
                .requestMatchers("/api/v1/dashboard/**").authenticated()
                .requestMatchers("/api/v1/logs/search").authenticated()
                .requestMatchers("/api/v1/exceptions/**").authenticated()
                .requestMatchers("/api/v1/ai/**").authenticated()
                .requestMatchers("/api/v1/github/**").authenticated()
                .requestMatchers("/api/v1/projects/**").authenticated()
                .requestMatchers("/api/v1/settings/**").authenticated()
                .requestMatchers("/api/v1/service-groups/**").authenticated()
                .requestMatchers("/api/v1/traces/**").authenticated()
                .requestMatchers("/api/v1/monitoring/**").authenticated()
                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );
        
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new RolesClaimConverter());
        return converter;
    }

    private static class RolesClaimConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Object rolesObj = jwt.getClaim("roles");
            if (rolesObj == null) {
                return Collections.emptyList();
            }
            
            if (rolesObj instanceof Collection<?> roles) {
                return roles.stream()
                        .map(Object::toString)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .collect(Collectors.toList());
            }
            
            return Collections.emptyList();
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
            "http://localhost:4200",
            "http://localhost:4206",
            "http://localhost:4220",
            "https://loghealer.reddia-x.com"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
