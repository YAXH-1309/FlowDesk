package com.flowdesk.auth.service;

import com.flowdesk.auth.domain.Tenant;
import com.flowdesk.auth.domain.User;
import com.flowdesk.auth.repository.TenantRepository;
import com.flowdesk.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExternalAuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    public ExternalAuthService(UserRepository userRepository, TenantRepository tenantRepository) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
    }

    /**
     * Finds an existing user by email or creates a new user+tenant on first login
     * via an external identity provider (OAuth2 or SAML).
     */
    @Transactional
    public User findOrCreateUser(String email, String provider) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            Tenant tenant = new Tenant();
            tenant.setName(email);
            tenant = tenantRepository.save(tenant);

            User user = new User();
            user.setEmail(email);
            user.setTenantId(tenant.getId());
            user.setRoles(new String[]{"MEMBER"});
            // Not a real hash — marker indicating external auth provider
            user.setPasswordHash("EXTERNAL_AUTH_" + provider.toUpperCase());
            return userRepository.save(user);
        });
    }
}
