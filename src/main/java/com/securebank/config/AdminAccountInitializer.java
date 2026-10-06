package com.securebank.config;

import com.securebank.entity.Customer;
import com.securebank.entity.Role;
import com.securebank.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final AdminProperties adminProperties;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountInitializer(AdminProperties adminProperties, CustomerRepository customerRepository,
                                   PasswordEncoder passwordEncoder) {
        this.adminProperties = adminProperties;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!adminProperties.isConfigured()) {
            log.info("ADMIN_BOOTSTRAP_SKIPPED reason=no admin credentials configured");
            return;
        }
        String email = adminProperties.email().trim().toLowerCase(Locale.ROOT);
        if (customerRepository.existsByEmail(email)) {
            return;
        }
        customerRepository.save(new Customer(adminProperties.fullName(), email, adminProperties.phone(),
                passwordEncoder.encode(adminProperties.password()), Role.ADMIN));
        log.info("ADMIN_BOOTSTRAP_CREATED");
    }
}
