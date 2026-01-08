package com.gestao.api.config;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender(
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.port:0}") int port,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${spring.mail.properties.mail.smtp.auth:false}") boolean auth,
            @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}") boolean starttls,
            @Value("${spring.mail.properties.mail.smtp.connectiontimeout:5000}") int connectionTimeout,
            @Value("${spring.mail.properties.mail.smtp.timeout:5000}") int timeout,
            @Value("${spring.mail.properties.mail.smtp.writetimeout:5000}") int writeTimeout
    ) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        if (!host.isEmpty()) {
            sender.setHost(host);
            if (port > 0) sender.setPort(port);
        }
        if (!username.isEmpty()) {
            sender.setUsername(username);
            sender.setPassword(password);
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", String.valueOf(auth));
        props.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        props.put("mail.smtp.connectiontimeout", String.valueOf(connectionTimeout));
        props.put("mail.smtp.timeout", String.valueOf(timeout));
        props.put("mail.smtp.writetimeout", String.valueOf(writeTimeout));
        return sender;
    }
}
