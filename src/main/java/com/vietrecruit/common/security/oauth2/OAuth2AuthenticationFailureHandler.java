package com.vietrecruit.common.security.oauth2;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final HttpCookieOAuth2AuthorizationRequestRepository
            cookieAuthorizationRequestRepository;

    @Value("${spring.application.frontend-url}")
    private String frontendBaseUrl;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException {

        log.error("OAuth2 authentication failed: {}", exception.getMessage());

        cookieAuthorizationRequestRepository.removeAuthorizationRequestCookies(request, response);

        String redirectUrl =
                String.format(
                        "%s/oauth2/callback?error=%s",
                        frontendBaseUrl,
                        URLEncoder.encode(exception.getMessage(), StandardCharsets.UTF_8));

        response.sendRedirect(redirectUrl);
    }
}
