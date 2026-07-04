package com.notificacao_api.config;

import java.util.Arrays;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.notificacao_api.security.ApiKeyAuthenticationFilter;
import com.notificacao_api.security.JwtAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguracao {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
            JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/ws-sockjs/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/h2-console/**")
                        .permitAll()
                        .requestMatchers("/admin/**").hasAuthority("GLOBAL_SUPER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/app/notificacoes/enviar")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_USER", "SCOPE_NOTIFICACOES_ENVIAR")
                        .requestMatchers(HttpMethod.POST, "/app/notificacoes/templates/enviar")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_USER", "SCOPE_NOTIFICACOES_ENVIAR")
                        .requestMatchers(HttpMethod.GET, "/app/notificacoes/fila")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_USER", "SCOPE_NOTIFICACOES_CONSULTAR")
                        .requestMatchers(HttpMethod.POST, "/app/contatos/consentimento")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_USER", "SCOPE_CONTATOS_GERENCIAR")
                        .requestMatchers(HttpMethod.GET, "/app/integracao/status")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_USER", "GLOBAL_API_KEY")
                        .requestMatchers("/app/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_USER")
                        .anyRequest().authenticated())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter jwtAuthenticationFilter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(
                jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<ApiKeyAuthenticationFilter> apiKeyAuthenticationFilterRegistration(
            ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) {
        FilterRegistrationBean<ApiKeyAuthenticationFilter> registration = new FilterRegistrationBean<>(
                apiKeyAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuracao) throws Exception {
        return configuracao.getAuthenticationManager();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuracao = new CorsConfiguration();
        configuracao.setAllowedOrigins(Arrays.asList(
                "http://localhost:4200",
                "http://127.0.0.1:4200",
                "http://localhost:56380",
                "http://127.0.0.1:56380",
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "https://notificacao.ramoncode.com.br",
                "https://api-notificacao.ramoncode.com.br"));
        configuracao.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuracao.setAllowedHeaders(Arrays.asList("*"));
        configuracao.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuracao);
        return source;
    }
}
