package com.gestao.api.security.controller;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${security.session.idle-expiration-seconds}")
    private long sessionIdleExpirationSeconds;
    
    public void storeToken(Long userId, String token) {
        String key = generateKey(userId);
        redisTemplate.delete(key);
        redisTemplate
                .opsForValue()
                .set(key, token, sessionIdleExpirationSeconds, TimeUnit.SECONDS);
    }

    public String getToken(Long userId) {
        return redisTemplate.opsForValue().get(generateKey(userId));
    }

    public void refreshSession(Long userId) {
        String key = generateKey(userId);
        redisTemplate.expire(key, sessionIdleExpirationSeconds, TimeUnit.SECONDS);
    }

    public void removeToken(Long userId) {
        redisTemplate.delete(generateKey(userId));
    }

    private String generateKey(Long userId) {
        return "user:session:" + userId;
    }
}
