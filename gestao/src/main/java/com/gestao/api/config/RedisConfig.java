package com.gestao.api.config;

import java.time.Duration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

@Configuration
public class RedisConfig {

	@Bean
	public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {

	    ObjectMapper mapper = new ObjectMapper();
	    mapper.registerModule(new JavaTimeModule());
	    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	    mapper.activateDefaultTyping(
	            LaissezFaireSubTypeValidator.instance,
	            ObjectMapper.DefaultTyping.NON_FINAL,
	            JsonTypeInfo.As.PROPERTY
	    );

	    GenericJackson2JsonRedisSerializer serializer =
	            new GenericJackson2JsonRedisSerializer(mapper);

	    RedisCacheConfiguration defaultConfig = RedisCacheConfiguration
	            .defaultCacheConfig()
	            .entryTtl(Duration.ofMinutes(55))   // TTL padrão 10 min
	            .disableCachingNullValues()
	            .serializeValuesWith(
	                    RedisSerializationContext.SerializationPair.fromSerializer(serializer)
	            );

	    return RedisCacheManager
	            .builder(connectionFactory)
	            .cacheDefaults(defaultConfig)
	            .build();
	}

}
