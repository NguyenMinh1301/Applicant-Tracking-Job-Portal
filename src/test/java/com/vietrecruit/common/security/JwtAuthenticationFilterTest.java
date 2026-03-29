package com.vietrecruit.common.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

/**
 * Unit tests for JwtAuthenticationFilter.
 *
 * <p>On JWT parse/validation failure the filter writes a 401 JSON response directly and short-
 * circuits the chain. For blacklisted tokens it continues the chain without setting authentication.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtService jwtService;
    @Mock private AuthCacheService authCacheService;
    @Mock private ObjectMapper objectMapper;

    private JwtAuthenticationFilter filter;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, authCacheService, objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(new DummyController()).addFilter(filter).build();
        SecurityContextHolder.clearContext();
    }

    // ── Scenario 1 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Missing Authorization header → chain continues, no JWT processing")
    void missingAuthHeader_chainContinues_noInteractions() throws Exception {
        mockMvc.perform(get("/test")).andExpect(status().isOk());

        verifyNoInteractions(jwtService, authCacheService);
    }

    // ── Scenario 2 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Non-Bearer Authorization header → chain continues, no JWT processing")
    void nonBearerPrefix_chainContinues_noInteractions() throws Exception {
        mockMvc.perform(get("/test").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isOk());

        verifyNoInteractions(jwtService, authCacheService);
    }

    // ── Scenario 3 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid token → parseAndValidate called, blacklist checked, authentication set")
    void validToken_authenticationSetInSecurityContext() throws Exception {
        String token = "valid.jwt.token";
        UUID userId = UUID.randomUUID();
        String jti = UUID.randomUUID().toString();
        Claims claims = mock(Claims.class);

        when(jwtService.parseAndValidate(token)).thenReturn(claims);
        when(jwtService.extractJti(claims)).thenReturn(jti);
        when(authCacheService.isBlacklisted(jti)).thenReturn(false);
        when(jwtService.extractUserId(claims)).thenReturn(userId);
        when(jwtService.extractRoles(claims)).thenReturn(Set.of("CANDIDATE"));
        when(authCacheService.getPermissions(userId)).thenReturn(null);

        mockMvc.perform(get("/test").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        verify(jwtService).parseAndValidate(token);
        verify(jwtService).extractJti(claims);
        verify(authCacheService).isBlacklisted(jti);
        verify(jwtService).extractUserId(claims);
        verify(jwtService).extractRoles(claims);
        verify(authCacheService).getPermissions(userId);
    }

    // ── Scenario 4 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Expired token (ExpiredJwtException) → filter writes 401, chain short-circuited")
    void expiredToken_returns401WithoutCallingChain() throws Exception {
        String token = "expired.jwt.token";
        when(jwtService.parseAndValidate(token))
                .thenThrow(new ExpiredJwtException(null, null, "Token expired"));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"success\":false}");

        mockMvc.perform(get("/test").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        verify(jwtService).parseAndValidate(token);
        verify(objectMapper).writeValueAsString(any());
        verifyNoInteractions(authCacheService);
        verify(jwtService, never()).extractUserId(any());
    }

    // ── Scenario 5 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Tampered/invalid token (JwtException) → filter writes 401, chain short-circuited")
    void invalidToken_returns401WithoutCallingChain() throws Exception {
        String token = "tampered.token";
        when(jwtService.parseAndValidate(token)).thenThrow(new JwtException("Invalid signature"));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"success\":false}");

        mockMvc.perform(get("/test").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        verify(jwtService).parseAndValidate(token);
        verify(objectMapper).writeValueAsString(any());
        verifyNoInteractions(authCacheService);
    }

    // ── Scenario 6 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Blacklisted jti → chain continues, extractUserId never called")
    void blacklistedToken_chainContinues_userIdNeverExtracted() throws Exception {
        String token = "blacklisted.jwt.token";
        String jti = UUID.randomUUID().toString();
        Claims claims = mock(Claims.class);

        when(jwtService.parseAndValidate(token)).thenReturn(claims);
        when(jwtService.extractJti(claims)).thenReturn(jti);
        when(authCacheService.isBlacklisted(jti)).thenReturn(true);

        mockMvc.perform(get("/test").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        verify(authCacheService).isBlacklisted(jti);
        verify(jwtService, never()).extractUserId(any());
        verify(jwtService, never()).extractRoles(any());
    }

    // ── Dummy controller ───────────────────────────────────────────────────

    @RestController
    static class DummyController {
        @GetMapping("/test")
        public String test() {
            return "OK";
        }
    }
}
