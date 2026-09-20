package com.gestao.api.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.gen.core.contracts.RolesProvider;
import com.gen.core.contracts.UserAccountLoader;
import com.gen.core.security.AbstractAuthenticationProvider;

@Component
public class AtelieAuthenticationProvider extends AbstractAuthenticationProvider {

    public AtelieAuthenticationProvider(UserAccountLoader userAccountLoader,
                                        PasswordEncoder passwordEncoder,
                                        RolesProvider rolesProvider) {
        super(userAccountLoader, passwordEncoder, rolesProvider);
    }
}
