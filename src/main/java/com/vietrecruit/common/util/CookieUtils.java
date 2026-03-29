package com.vietrecruit.common.util;

import java.util.Base64;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.ResponseCookie;
import org.springframework.security.jackson2.SecurityJackson2Modules;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CookieUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.registerModules(
                SecurityJackson2Modules.getModules(CookieUtils.class.getClassLoader()));
    }

    public static Optional<Cookie> getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(name)) {
                return Optional.of(cookie);
            }
        }
        return Optional.empty();
    }

    public static void addCookie(
            HttpServletResponse response, String name, String value, int maxAge) {
        ResponseCookie cookie =
                ResponseCookie.from(name, value)
                        .httpOnly(true)
                        .secure(true)
                        .sameSite("Lax")
                        .path("/")
                        .maxAge(maxAge)
                        .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public static void deleteCookie(
            HttpServletRequest request, HttpServletResponse response, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return;
        }
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(name)) {
                cookie.setValue("");
                cookie.setPath("/");
                cookie.setMaxAge(0);
                response.addCookie(cookie);
            }
        }
    }

    public static String serialize(Object object) {
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(object);
            return Base64.getUrlEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize object to JSON", e);
        }
    }

    public static <T> T deserialize(Cookie cookie, Class<T> cls) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(cookie.getValue());
            return MAPPER.readValue(bytes, cls);
        } catch (Exception e) {
            log.warn(
                    "Corrupted cookie '{}' — deserialization failed, discarding: {}",
                    cookie.getName(),
                    e.getMessage());
            return null;
        }
    }
}
