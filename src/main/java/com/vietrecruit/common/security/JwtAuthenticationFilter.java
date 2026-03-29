package com.vietrecruit.common.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
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
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AuthCacheService authCacheService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.parseAndValidate(token);
                String jti = jwtService.extractJti(claims);

                if (authCacheService.isBlacklisted(jti)) {
                    log.debug("Token blacklisted: jti={}", jti);
                    filterChain.doFilter(request, response);
                    return;
                }

                UUID userId = jwtService.extractUserId(claims);
                Set<String> roles = jwtService.extractRoles(claims);

                // Build authorities: ROLE_ prefixed roles + permission codes from cache
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                for (String role : roles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }

                Set<String> permissions = authCacheService.getPermissions(userId);
                if (permissions != null) {
                    for (String perm : permissions) {
                        authorities.add(new SimpleGrantedAuthority(perm));
                    }
                }

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userId.toString(), null, authorities);
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);
            } catch (Exception e) {
                log.debug("JWT authentication failed: {}", e.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                try {
                    ApiResponse<Void> body =
                            ApiResponse.failure(
                                    ApiErrorCode.AUTH_TOKEN_INVALID,
                                    ApiErrorCode.AUTH_TOKEN_INVALID.getDefaultMessage(),
                                    null);
                    response.getWriter().write(objectMapper.writeValueAsString(body));
                } catch (IOException ioEx) {
                    log.error("Failed to write 401 response: {}", ioEx.getMessage());
                }
                return;
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
}
