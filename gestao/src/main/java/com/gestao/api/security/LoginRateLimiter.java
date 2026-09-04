package com.gestao.api.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.gen.core.security.SessionService;

@Service
public class LoginRateLimiter {

    private final SessionService session;

    @Value("${security.login.max-attempts-per-ip:10}")	
    private int maxAttemptsPerIp;


    @Value("${security.login.max-attempts-per-ip-email:5}")
    private int maxAttemptsPerIpEmail;

    @Value("${security.login.window-seconds:900}") 
    private int windowSeconds;

    public LoginRateLimiter(SessionService session) {
        this.session = session;
    }

    private String keyIp(String ip) {
        return "login:ip:" + ip;
    }

    private String keyEmail(String email) {
        return "login:email:" + email.toLowerCase().trim();
    }

    private String keyIpEmail(String ip, String email) {
        return "login:ip_email:" + ip + ":" + email.toLowerCase().trim();
    }

    public boolean isBlocked(String ip, String email) {
        String ipKey = keyIp(ip);
        String ipEmailKey = keyIpEmail(ip, email);

        int ipCount = getCount(ipKey);
        int ipEmailCount = getCount(ipEmailKey);

        return ipCount >= maxAttemptsPerIp
            || ipEmailCount >= maxAttemptsPerIpEmail;
    }

    public void registerFailedAttempt(String ip, String email) {
        incrementWithTtl(keyIp(ip));
        incrementWithTtl(keyIpEmail(ip, email));
    }

    public void resetAttempts(String ip, String email) {
        session.delete(keyIp(ip));
        session.delete(keyIpEmail(ip, email));
    }

    private int getCount(String key) {
        String value = session.get(key);
        if (value == null) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void incrementWithTtl(String key) {
        session.increment(key, windowSeconds);
    }
}
