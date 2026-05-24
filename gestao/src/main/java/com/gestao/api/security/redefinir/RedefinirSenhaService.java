package com.gestao.api.security.redefinir;

import lombok.SneakyThrows;
import org.springframework.security.core.token.KeyBasedPersistenceTokenService;
import org.springframework.security.core.token.SecureRandomFactoryBean;
import org.springframework.security.core.token.Token;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.gen.core.db.Condicao;
import com.gen.core.db.QueryBuilder;
import com.gen.core.db.TransactionDB;
import com.gen.core.security.SessionService;
import com.gestao.api.entities.Usuario;
import com.gestao.api.security.redefinir.dto.PasswordTokenPublicData;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
public class RedefinirSenhaService {

    private final TransactionDB trans;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;

    public RedefinirSenhaService(TransactionDB trans, PasswordEncoder passwordEncoder, SessionService sessionService) {
        this.trans = trans;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
    }

    @SneakyThrows
    public String generateToken(Usuario user) throws Exception {
        KeyBasedPersistenceTokenService tokenService = getInstanceFor(user);
        Token token = tokenService.allocateToken(user.getEmail());
        return token.getKey();
    }

    @SneakyThrows
    public void changePassword(String newPassword, String rawToken) throws Exception {
        PasswordTokenPublicData publicData = readPublicData(rawToken);

        if (isExpired(publicData)) {
            throw new RuntimeException("Token expirado");
        }

        Usuario usuario = new QueryBuilder(trans)
                .select()
                .from(Usuario.class)
                .where("email", Condicao.EQUAL, publicData.email())
                .one();

        KeyBasedPersistenceTokenService tokenService = this.getInstanceFor(usuario);
        tokenService.verifyToken(rawToken);

        usuario.setSenha(this.passwordEncoder.encode(newPassword));
        trans.update(usuario);
        sessionService.removeToken(usuario.getId());
    }

    private boolean isExpired(PasswordTokenPublicData publicData) {
        Instant createdAt = new Date(publicData.createAtTimestamp()).toInstant();
        Instant now = new Date().toInstant();
        return createdAt.plus(Duration.ofMinutes(5)).isBefore(now);
    }

    private KeyBasedPersistenceTokenService getInstanceFor(Usuario usuario) throws Exception {
        KeyBasedPersistenceTokenService tokenService = new KeyBasedPersistenceTokenService();
        tokenService.setServerSecret(usuario.getSenha());
        tokenService.setServerInteger(16);
        tokenService.setSecureRandom(new SecureRandomFactoryBean().getObject());
        return tokenService;
    }

    private PasswordTokenPublicData readPublicData(String rawToken) {
        String rawTokenDecoded = new String(Base64.getDecoder().decode(rawToken));
        String[] tokenParts = rawTokenDecoded.split(":");
        Long timestamp = Long.parseLong(tokenParts[0]);
        String email = tokenParts[2];
        return new PasswordTokenPublicData(email, timestamp);
    }
}
