package com.retailerp.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first platform admin from PLATFORM_ADMIN_EMAIL / PLATFORM_ADMIN_PASSWORD, if no admin with that email
 * exists yet. An {@link ApplicationRunner} runs once, right after the application has started.
 * The password comes from the environment so it's never committed to git.
 */
@Component
public class PlatformBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformBootstrap.class);

    private final PlatformAdminRepository admins;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public PlatformBootstrap(PlatformAdminRepository admins, PasswordEncoder passwordEncoder,
            @Value("${app.platform.admin-email:}") String email, @Value("${app.platform.admin-password:}") String password) {
        this.admins = admins;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            if (admins.count() == 0) {
                log.info("No platform admin yet. Set PLATFORM_ADMIN_EMAIL and PLATFORM_ADMIN_PASSWORD to create one.");
            }
            return;
        }
        if (password.length() < 12) {
            throw new IllegalStateException("PLATFORM_ADMIN_PASSWORD must be at least 12 characters.");
        }
        if (admins.findByEmailIgnoreCase(email.trim()).isEmpty()) {
            admins.save(new PlatformAdmin(email, passwordEncoder.encode(password), "Platform admin"));
            log.info("Created platform admin {}", email);
        }
    }
}
