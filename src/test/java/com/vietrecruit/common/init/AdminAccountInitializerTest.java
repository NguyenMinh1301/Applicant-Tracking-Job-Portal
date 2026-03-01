package com.vietrecruit.common.init;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.vietrecruit.feature.user.entity.Role;
import com.vietrecruit.feature.user.entity.User;
import com.vietrecruit.feature.user.repository.RoleRepository;
import com.vietrecruit.feature.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminAccountInitializerTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private AdminAccountInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer =
                new AdminAccountInitializer(
                        userRepository,
                        roleRepository,
                        passwordEncoder,
                        "admin@vietrecruit.site",
                        "Admin@VR2026!");
    }

    @Test
    @DisplayName("Should create admin account when none exists")
    void run_CreatesAdmin() {
        var role = Role.builder().id((short) 1).code("SYSTEM_ADMIN").name("System Admin").build();

        when(userRepository.existsByEmail("admin@vietrecruit.site")).thenReturn(false);
        when(roleRepository.findByCode("SYSTEM_ADMIN")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(any())).thenReturn("$2a$hashed");

        initializer.run(new DefaultApplicationArguments());

        var captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        var admin = captor.getValue();
        assertEquals("admin@vietrecruit.site", admin.getEmail());
        assertEquals("$2a$hashed", admin.getPasswordHash());
        assertEquals("System Administrator", admin.getFullName());
        assertTrue(admin.getEmailVerified());
        assertTrue(admin.getIsActive());
        assertFalse(admin.getIsLocked());
        assertTrue(admin.getRoles().contains(role));
    }

    @Test
    @DisplayName("Should skip creation when admin already exists")
    void run_SkipsWhenExists() {
        when(userRepository.existsByEmail("admin@vietrecruit.site")).thenReturn(true);

        initializer.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when SYSTEM_ADMIN role is missing")
    void run_ThrowsWhenRoleMissing() {
        when(userRepository.existsByEmail("admin@vietrecruit.site")).thenReturn(false);
        when(roleRepository.findByCode("SYSTEM_ADMIN")).thenReturn(Optional.empty());

        assertThrows(
                IllegalStateException.class,
                () -> initializer.run(new DefaultApplicationArguments()));
    }
}
