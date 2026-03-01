package com.vietrecruit.common.init;

import java.time.Instant;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.vietrecruit.feature.user.entity.User;
import com.vietrecruit.feature.user.repository.RoleRepository;
import com.vietrecruit.feature.user.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Seeds a default SYSTEM_ADMIN account on first startup if none exists.
 *
 * <p>Credentials are configured via application.yaml / .env properties.
 */
@Slf4j
@Component
@Order(1)
public class AdminAccountInitializer implements ApplicationRunner {

    private static final String ADMIN_FULL_NAME = "System Administrator";
    private static final String ROLE_SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminAccountInitializer(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            @Value("${vietrecruit.admin.email}") String adminEmail,
            @Value("${vietrecruit.admin.password}") String adminPassword) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Admin account already exists, skipping initialization");
            return;
        }

        var adminRole =
                roleRepository
                        .findByCode(ROLE_SYSTEM_ADMIN)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Role SYSTEM_ADMIN not found. Flyway migrations may not have run."));

        var admin =
                User.builder()
                        .email(adminEmail)
                        .passwordHash(passwordEncoder.encode(adminPassword))
                        .fullName(ADMIN_FULL_NAME)
                        .emailVerified(true)
                        .emailVerifiedAt(Instant.now())
                        .isActive(true)
                        .isLocked(false)
                        .roles(Set.of(adminRole))
                        .build();

        userRepository.save(admin);

        log.warn("==========================================================");
        log.warn("  DEFAULT ADMIN ACCOUNT CREATED");
        log.warn("  Email:    {}", adminEmail);
        log.warn("  Password: {}", adminPassword);
        log.warn("==========================================================");
    }
}
