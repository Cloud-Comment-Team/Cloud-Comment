package com.cloudcomment.shared.config;

import com.cloudcomment.shared.web.security.ApiAuthenticationEntryPoint;
import com.cloudcomment.shared.web.security.ApiAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class ApiSecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
        HttpSecurity http,
        ApiAuthenticationFilter apiAuthenticationFilter,
        ApiAuthenticationEntryPoint apiAuthenticationEntryPoint
    ) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(apiAuthenticationEntryPoint))
            .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
            .addFilterBefore(apiAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    FilterRegistrationBean<ApiAuthenticationFilter> apiAuthenticationFilterRegistration(
        ApiAuthenticationFilter apiAuthenticationFilter
    ) {
        FilterRegistrationBean<ApiAuthenticationFilter> registration = new FilterRegistrationBean<>(apiAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }
}
