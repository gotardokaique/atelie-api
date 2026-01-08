package com.gestao.api.security.controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class SessionService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final long TOKEN_EXPIRATION_SECONDS = 3600; 
    public void storeToken(Long userId, String token) {
        String key = generateKey(userId);
        redisTemplate.delete(key);
        redisTemplate.opsForValue().set(key, token, TOKEN_EXPIRATION_SECONDS, TimeUnit.SECONDS);
    }

    public String getToken(Long userId) {
        return redisTemplate.opsForValue().get(generateKey(userId));
    }

    public void removeToken(Long userId) {
        redisTemplate.delete(generateKey(userId));
    }

    private String generateKey(Long userId) {
        return "user:session:" + userId;
    }
}
