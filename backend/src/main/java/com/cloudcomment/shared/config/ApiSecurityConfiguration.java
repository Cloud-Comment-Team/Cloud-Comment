package com.cloudcomment.shared.config;

import com.cloudcomment.shared.web.security.ApiAuthenticationEntryPoint;
import com.cloudcomment.shared.web.security.ApiAuthenticationFilter;
import com.cloudcomment.shared.web.security.AdminCsrfFilter;
import com.cloudcomment.shared.web.security.AdminSessionProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(AdminSessionProperties.class)
public class ApiSecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
        HttpSecurity http,
        AdminCsrfFilter adminCsrfFilter,
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
            .addFilterBefore(adminCsrfFilter, ApiAuthenticationFilter.class)
            .build();
    }

    @Bean
    FilterRegistrationBean<AdminCsrfFilter> adminCsrfFilterRegistration(AdminCsrfFilter adminCsrfFilter) {
        FilterRegistrationBean<AdminCsrfFilter> registration = new FilterRegistrationBean<>(adminCsrfFilter);
        registration.setEnabled(false);
        return registration;
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
