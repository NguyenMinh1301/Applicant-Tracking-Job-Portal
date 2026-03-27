package com.vietrecruit.common.config.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.vietrecruit.common.security.EmailVerificationFilter;
import com.vietrecruit.common.security.JwtAuthenticationFilter;
import com.vietrecruit.common.security.oauth2.CustomOAuth2UserService;
import com.vietrecruit.common.security.oauth2.HttpCookieOAuth2AuthorizationRequestRepository;
import com.vietrecruit.common.security.oauth2.OAuth2AuthenticationFailureHandler;
import com.vietrecruit.common.security.oauth2.OAuth2AuthenticationSuccessHandler;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final EmailVerificationFilter emailVerificationFilter;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final HttpCookieOAuth2AuthorizationRequestRepository
            cookieAuthorizationRequestRepository;
    private final OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2FailureHandler;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    private static final String[] publicAuthEndpoints = {
        "/vietrecruit/auth/login",
        "/vietrecruit/auth/register",
        "/vietrecruit/auth/register/invite",
        "/vietrecruit/auth/refresh",
        "/vietrecruit/auth/forgot-password",
        "/vietrecruit/auth/reset-password",
        "/vietrecruit/auth/verify-otp",
        "/vietrecruit/auth/resend-otp",
        "/vietrecruit/auth/oauth2/exchange",
    };

    private static final String[] publicOAuth2Endpoints = {
        "/oauth2/**", "/login/oauth2/**",
    };

    private static final String[] publicOtherEndpoints = {
        "/vietrecruit/v3/api-docs/**",
        "/vietrecruit/api-docs/**",
        "/vietrecruit/swagger-ui.html",
        "/vietrecruit/swagger-ui/**",
        "/vietrecruit/plans/**",
        "/vietrecruit/webhooks/**",
        "/vietrecruit/jobs/public/**",
        "/vietrecruit/jobs/search",
        "/vietrecruit/jobs/autocomplete",
        "/vietrecruit/companies/search",
        "/health/**"
    };

    private static final String[] clientEndpoints = {"/vietrecruit/users/**"};
    private static final String[] adminEndpoints = {"/vietrecruit/admin/**"};

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.asList(allowedOrigins.split("\\s*,\\s*"));
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(publicAuthEndpoints)
                                        .permitAll()
                                        .requestMatchers(publicOAuth2Endpoints)
                                        .permitAll()
                                        .requestMatchers(publicOtherEndpoints)
                                        .permitAll()
                                        .requestMatchers(clientEndpoints)
                                        .authenticated()
                                        .requestMatchers(adminEndpoints)
                                        .hasAnyRole("SYSTEM_ADMIN", "COMPANY_ADMIN")
                                        .requestMatchers("/actuator/**")
                                        .hasRole("SYSTEM_ADMIN")
                                        .anyRequest()
                                        .authenticated())
                .oauth2Login(
                        oauth2 ->
                                oauth2.authorizationEndpoint(
                                                auth ->
                                                        auth.baseUri("/oauth2/authorize")
                                                                .authorizationRequestRepository(
                                                                        cookieAuthorizationRequestRepository))
                                        .redirectionEndpoint(
                                                redirect ->
                                                        redirect.baseUri(
                                                                "/vietrecruit/auth/oauth2/callback/*"))
                                        .userInfoEndpoint(
                                                userInfo ->
                                                        userInfo.userService(
                                                                customOAuth2UserService))
                                        .successHandler(oAuth2SuccessHandler)
                                        .failureHandler(oAuth2FailureHandler))
                .addFilterBefore(
                        jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(emailVerificationFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}
