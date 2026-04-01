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

/** Utility class for reading, writing, and serializing HTTP cookies using Jackson + Base64. */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CookieUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.registerModules(
                SecurityJackson2Modules.getModules(CookieUtils.class.getClassLoader()));
    }

    /**
     * Finds a cookie by name from the incoming request.
     *
     * @param request the HTTP request
     * @param name the cookie name to look up
     * @return Optional containing the matching cookie, or empty if not present
     */
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

    /**
     * Adds an HttpOnly, Secure, SameSite=Lax cookie to the response.
     *
     * @param response the HTTP response
     * @param name cookie name
     * @param value cookie value
     * @param maxAge max-age in seconds
     */
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

    /**
     * Expires an existing cookie by setting its max-age to zero.
     *
     * @param request the HTTP request
     * @param response the HTTP response
     * @param name cookie name to delete
     */
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

    /**
     * Serializes an object to a URL-safe Base64-encoded JSON string using Jackson with Spring
     * Security modules.
     *
     * @param object the object to serialize
     * @return URL-safe Base64 string
     * @throws IllegalStateException if serialization fails
     */
    public static String serialize(Object object) {
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(object);
            return Base64.getUrlEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize object to JSON", e);
        }
    }

    /**
     * Deserializes a cookie value from URL-safe Base64-encoded JSON into the target type. Returns
     * null and logs a warning if deserialization fails, so the caller can treat the cookie as
     * absent.
     *
     * @param cookie the cookie holding the Base64-encoded value
     * @param cls the target class
     * @param <T> the target type
     * @return deserialized object, or null on failure
     */
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
