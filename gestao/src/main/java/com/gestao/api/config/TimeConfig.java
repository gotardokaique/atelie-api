package com.gestao.api.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TimeConfig {

    public static final ZoneId RONDONIA_ZONE = ZoneId.of("America/Porto_Velho");

    @Bean
    public Clock clock() {
        return Clock.system(RONDONIA_ZONE);
    }
}
