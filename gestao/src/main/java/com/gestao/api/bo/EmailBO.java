package com.gestao.api.bo;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.gen.core.utils.StringEncryptUtils;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

@Component
public class EmailBO {

    @SuppressWarnings("unused")
    private final StringEncryptUtils encryptUtils;

    @Value("${spring.mail.username}")
    private String emailRemetenteDefault;

    @Value("${spring.mail.password}")
    private String senhaAppDefault;

    public EmailBO(StringEncryptUtils encryptUtils) {
        this.encryptUtils = encryptUtils;
    }

    /** Inicia um builder novo (thread-safe). */
    public Builder criar() {
        return new Builder(emailRemetenteDefault, senhaAppDefault);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static class Builder {

        private final String credEmailDefault;
        private final String credSenhaDefault;

        private String destinatario;
        private String remetente;
        private String senhaApp;
        private String titulo;
        private String corpo;

        private Builder(String credEmailDefault, String credSenhaDefault) {
            this.credEmailDefault = credEmailDefault;
            this.credSenhaDefault = credSenhaDefault;
        }

        public Builder destinatario(String email) {
            this.destinatario = email;
            return this;
        }

        public Builder remetente() {
            this.remetente = credEmailDefault;
            this.senhaApp = credSenhaDefault == null ? null : credSenhaDefault.replace(" ", "");
            return this;
        }

        public Builder remetente(String email, String senhaApp) {
            this.remetente = email;
            this.senhaApp = senhaApp == null ? null : senhaApp.replace(" ", "");
            return this;
        }

        public Builder titulo(String titulo) {
            this.titulo = titulo;
            return this;
        }

        public Builder mensagem(String corpo) {
            this.corpo = corpo;
            return this;
        }

        public Builder mensagem(String titulo, String corpo) {
            this.titulo = titulo;
            this.corpo = corpo;
            return this;
        }

        public void enviar() {
            validar();

            Properties props = buildSmtpProperties();

            final String fromAddr = remetente;
            final String pwd = senhaApp;

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(fromAddr, pwd);
                }
            });

            try {
                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(fromAddr));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
                message.setSubject(titulo, "UTF-8");
                message.setContent(corpo, "text/html; charset=UTF-8");

                Transport.send(message);

            } catch (MessagingException e) {
                throw new RuntimeException("Falha ao enviar e-mail para: " + destinatario, e);
            }
        }

        private Properties buildSmtpProperties() {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", "smtp.gmail.com");
            props.put("mail.smtp.port", "587");
            props.put("mail.smtp.ssl.trust", "smtp.gmail.com");
            return props;
        }

        private void validar() {
            if (destinatario == null || destinatario.isBlank())
                throw new IllegalStateException("Destinatário não informado.");
            if (remetente == null || remetente.isBlank())
                throw new IllegalStateException("Remetente não informado.");
            if (senhaApp == null || senhaApp.isBlank())
                throw new IllegalStateException("Senha de app não informada.");
            if (titulo == null || titulo.isBlank())
                throw new IllegalStateException("Título do e-mail não informado.");
            if (corpo == null || corpo.isBlank())
                throw new IllegalStateException("Corpo do e-mail não informado.");
        }
    }
}
