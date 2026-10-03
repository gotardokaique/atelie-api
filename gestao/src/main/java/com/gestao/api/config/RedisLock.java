package com.gestao.api.config;

import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Lock/marcador distribuído com SET NX + TTL. Se o Redis falhar, nega (o job pula a rodada). */
@Component
public class RedisLock {

    private static final Logger log = LoggerFactory.getLogger(RedisLock.class);
    private static final String PREFIXO = "atelie:lock:";

    private final StringRedisTemplate redis;
    private final String instancia = UUID.randomUUID().toString();

    public RedisLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean adquirir(String chave, Duration ttl) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(PREFIXO + chave, instancia, ttl));
        } catch (Exception e) {
            log.warn("[LOCK] Redis indisponível ao adquirir '{}': {}", chave, e.getMessage());
            return false;
        }
    }

    public boolean existe(String chave) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(PREFIXO + chave));
        } catch (Exception e) {
            log.warn("[LOCK] Redis indisponível ao consultar '{}': {}", chave, e.getMessage());
            return true;
        }
    }
}
