package com.gestao.api.config.lia;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.gestao.api.config.lia.properties.LiaProperties;

@Configuration
@EnableConfigurationProperties(LiaProperties.class)
public class LiaConfig {
}
