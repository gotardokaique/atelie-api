package com.gestao.api.config;
import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    public static final ZoneId RONDONIA_ZONE = ZoneId.of("America/Porto_Velho");

    @Bean
    public Clock clock() {
        return Clock.system(RONDONIA_ZONE);
    }
}
