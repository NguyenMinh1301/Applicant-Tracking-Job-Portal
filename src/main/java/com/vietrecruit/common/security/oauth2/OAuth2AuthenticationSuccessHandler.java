package com.vietrecruit.common.security.oauth2;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.vietrecruit.feature.auth.dto.response.LoginResponse;
import com.vietrecruit.feature.auth.service.AuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final HttpCookieOAuth2AuthorizationRequestRepository
            cookieAuthorizationRequestRepository;

    @Value("${spring.application.frontend-url}")
    private String frontendBaseUrl;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = oauthToken.getPrincipal();
        String registrationId = oauthToken.getAuthorizedClientRegistrationId();

        String email = extractEmail(oAuth2User, registrationId);
        String providerUserId = extractProviderUserId(oAuth2User, registrationId);
        String name = extractName(oAuth2User);
        String avatarUrl = extractAvatarUrl(oAuth2User, registrationId);

        log.info("OAuth2 login success: provider={}, email={}", registrationId, email);

        LoginResponse loginResponse =
                authService.processOAuth2Login(
                        registrationId.toUpperCase(), email, providerUserId, name, avatarUrl);

        String redirectUrl =
                String.format(
                        "%s/oauth2/callback?token=%s&refresh=%s&expires_in=%d",
                        frontendBaseUrl,
                        URLEncoder.encode(loginResponse.getAccessToken(), StandardCharsets.UTF_8),
                        URLEncoder.encode(loginResponse.getRefreshToken(), StandardCharsets.UTF_8),
                        loginResponse.getExpiresIn());

        cookieAuthorizationRequestRepository.removeAuthorizationRequestCookies(request, response);
        response.sendRedirect(redirectUrl);
    }

    private String extractEmail(OAuth2User oAuth2User, String registrationId) {
        String email = oAuth2User.getAttribute("email");
        if (email == null && "github".equals(registrationId)) {
            email = oAuth2User.getAttribute("email");
        }
        return email;
    }

    private String extractProviderUserId(OAuth2User oAuth2User, String registrationId) {
        if ("google".equals(registrationId)) {
            return oAuth2User.getAttribute("sub");
        }
        Object id = oAuth2User.getAttribute("id");
        return id != null ? id.toString() : oAuth2User.getName();
    }

    private String extractName(OAuth2User oAuth2User) {
        String name = oAuth2User.getAttribute("name");
        if (name == null) {
            name = oAuth2User.getAttribute("login");
        }
        return name;
    }

    private String extractAvatarUrl(OAuth2User oAuth2User, String registrationId) {
        if ("google".equals(registrationId)) {
            return oAuth2User.getAttribute("picture");
        }
        return oAuth2User.getAttribute("avatar_url");
    }
}
