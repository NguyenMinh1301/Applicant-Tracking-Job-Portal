package com.vietrecruit.common.security;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.response.ApiResponse;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private static final Set<String> SKIP_PATTERNS =
            Set.of(
                    "/vietrecruit/auth/**",
                    "/oauth2/**",
                    "/login/oauth2/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/actuator/**",
                    "/health/**");

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (shouldSkip(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract email_verified from JWT claims — no DB query
        String token = extractToken(request);
        if (token != null) {
            try {
                Claims claims = jwtService.parseAndValidate(token);
                boolean emailVerified = jwtService.extractEmailVerified(claims);

                if (!emailVerified) {
                    log.debug("Blocked unverified user: userId={}", authentication.getPrincipal());
                    writeErrorResponse(response);
                    return;
                }
            } catch (Exception e) {
                // Token already validated by JwtAuthenticationFilter; if parsing fails here
                // it means the token is invalid so we let the request proceed (auth filter
                // will have already rejected it or it's a non-JWT auth).
                log.debug("Could not parse token for email verification check: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private boolean shouldSkip(String requestUri) {
        return SKIP_PATTERNS.stream().anyMatch(pattern -> pathMatcher.match(pattern, requestUri));
    }

    private void writeErrorResponse(HttpServletResponse response) throws IOException {
        ApiErrorCode errorCode = ApiErrorCode.AUTH_EMAIL_NOT_VERIFIED;
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> body = ApiResponse.failure(errorCode);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
