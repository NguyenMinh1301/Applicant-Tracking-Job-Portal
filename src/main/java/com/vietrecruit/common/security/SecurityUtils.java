package com.vietrecruit.common.security;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;

/**
 * Utility class providing static helpers for extracting authentication data from the security
 * context.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Returns the UUID of the currently authenticated user.
     *
     * @return current user's UUID
     * @throws com.vietrecruit.common.exception.ApiException with UNAUTHORIZED if no valid principal
     */
    public static UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED);
        }
        try {
            return UUID.fromString(auth.getPrincipal().toString());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED);
        }
    }

    /**
     * Returns the current user's UUID wrapped in an Optional, or empty for anonymous requests.
     *
     * @return Optional containing the user UUID, or empty if unauthenticated
     */
    public static Optional<UUID> getCurrentUserIdOptional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || auth.getPrincipal() == null
                || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(auth.getPrincipal().toString()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Returns the set of role codes (without the {@code ROLE_} prefix) for the current user.
     *
     * @return set of role code strings, empty if unauthenticated
     */
    public static Set<String> getCurrentRoles() {
        return getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .collect(Collectors.toSet());
    }

    /**
     * Returns true if the current user holds the specified permission code as a granted authority.
     *
     * @param permissionCode the exact permission code to check
     * @return {@code true} if the authority is present
     */
    public static boolean hasPermission(String permissionCode) {
        return getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals(permissionCode));
    }

    private static Collection<? extends GrantedAuthority> getAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Set.of();
        }
        return auth.getAuthorities();
    }
}
