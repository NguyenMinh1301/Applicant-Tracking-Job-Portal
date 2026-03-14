package com.vietrecruit.common.security;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;

@ExtendWith(MockitoExtension.class)
class EmailVerificationFilterTest {

    private MockMvc mockMvc;

    @Mock private JwtService jwtService;

    private EmailVerificationFilter filter;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper =
                new ObjectMapper()
                        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        filter = new EmailVerificationFilter(jwtService, objectMapper);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should pass through for public auth endpoints")
    void publicEndpoint_PassesThrough() throws Exception {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new Object() {})
                        .addFilter(filter, "/vietrecruit/auth/*")
                        .build();

        mockMvc.perform(get("/vietrecruit/auth/login")).andExpect(status().isNotFound());

        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Should pass through when no authentication present")
    void noAuthentication_PassesThrough() throws Exception {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new Object() {})
                        .addFilter(filter, "/vietrecruit/users/*")
                        .build();

        mockMvc.perform(get("/vietrecruit/users/me")).andExpect(status().isNotFound());

        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Should block unverified authenticated user with 403")
    void unverifiedUser_Returns403() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = "valid.jwt.token";

        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                userId.toString(), null, Collections.emptyList()));

        Claims claims = mock(Claims.class);

        when(jwtService.parseAndValidate(token)).thenReturn(claims);
        when(jwtService.extractEmailVerified(claims)).thenReturn(false);

        mockMvc =
                MockMvcBuilders.standaloneSetup(new DummyController())
                        .addFilter(filter, "/*")
                        .build();

        mockMvc.perform(get("/protected-resource").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_EMAIL_NOT_VERIFIED"));
    }

    @Test
    @DisplayName("Should allow verified authenticated user through")
    void verifiedUser_PassesThrough() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = "valid.jwt.token";

        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                userId.toString(), null, Collections.emptyList()));

        Claims claims = mock(Claims.class);

        when(jwtService.parseAndValidate(token)).thenReturn(claims);
        when(jwtService.extractEmailVerified(claims)).thenReturn(true);

        mockMvc =
                MockMvcBuilders.standaloneSetup(new DummyController())
                        .addFilter(filter, "/*")
                        .build();

        mockMvc.perform(get("/protected-resource").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @org.springframework.web.bind.annotation.RestController
    static class DummyController {
        @org.springframework.web.bind.annotation.GetMapping("/protected-resource")
        public String protectedResource() {
            return "OK";
        }
    }
}
