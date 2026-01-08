package com.gestao.api.security.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.from:no-reply@genfinance.com}")
    private String mailFrom;

    public void sendPasswordResetCode(String toEmail, String code) {
        String html =
            "<div style='font-family:Segoe UI,Arial,sans-serif;background:#f5f6fa;padding:40px 0;width:100%'>" +
                "<div style='max-width:480px;margin:0 auto;background:#fff;border-radius:10px;box-shadow:0 4px 24px #0001;overflow:hidden'>" +
                    "<div style='background:#004d40;padding:28px 40px'>" +
                        "<img src='https://genfinance.com.br/genfinance-logo.png' alt='GenFinance' style='height:38px;margin-bottom:14px;display:block'/>" +
                        "<h1 style='margin:0;color:#e0f7fa;font-size:1.5rem;font-weight:700;'>Redefinição de senha</h1>" +
                    "</div>" +
                    "<div style='padding:32px 40px'>" +
                        "<p style='color:#222;font-size:1.13rem;margin-bottom:18px'>Seu código para redefinir senha:</p>" +
                        "<div style='font-size:2rem;font-weight:700;letter-spacing:4px;padding:18px 0;background:#e0f7fa;color:#009688;text-align:center;border-radius:7px;margin-bottom:18px;border:1px solid #b2dfdb'>" +
                            code +
                        "</div>" +
                        "<p style='font-size:1rem;color:#555;margin:20px 0'>Este código é válido por 5 minutos. Se você não solicitou esta operação, apenas ignore.</p>" +
                    "</div>" +
                    "<div style='background:#f1f8e9;text-align:center;padding:20px;color:#555;font-size:13px;border-top:1px solid #e0f7fa'>" +
                        "GenFinance — Controle Financeiro Pessoal<br>Não responda este e-mail." +
                    "</div>" +
                "</div>" +
            "</div>";
        sendHtmlMessage(toEmail, "Seu Código de Redefinição de Senha", html);
    }

    public void sendFeedbackMessage(String to, String subject, String body, String userEmail, Long userId) {
        String html =
            "<div style='font-family:Segoe UI, Arial, sans-serif; background:#f5f6fa; padding:40px 0; min-height:100vh;'>" +
            "  <div style='max-width:560px;margin:0 auto;background:#fff;border-radius:18px;box-shadow:0 8px 32px #00968822;overflow:hidden;border:1.5px solid #00968833;'>" +
            "    <div style='background:#08584e;padding:34px 38px 22px 38px;text-align:center'>" +
            "      <img src='https://genfinance.com.br/genfinance-logo.png' alt='GenFinance' style='height:46px;margin-bottom:14px;display:inline-block;'/><br/>" +
            "      <span style='display:block;font-size:2.1rem;color:#e0f7fa;font-weight:800;letter-spacing:-2px;margin-bottom:6px;'>GenFinance</span>" +
            "    </div>" +
            "    <div style='padding:36px 38px 34px 38px'>" +
            "      <h2 style='color:#009688;font-size:1.35rem;margin:0 0 8px 0;font-weight:700;'>Novo feedback recebido</h2>" +
            "      <p style='color:#222;font-size:1.07rem;line-height:1.6;margin-bottom:18px;margin-top:0'>Você recebeu um novo feedback pelo sistema GenFinance. Veja os detalhes:</p>" +
            "      <table style='width:100%;border-collapse:collapse;font-size:1rem;margin-bottom:18px'>" +
            "        <tr>" +
            "          <td style='color:#757575;padding:5px 0 5px 0;width:125px;font-weight:600;'>ID Usuário:</td>" +
            "          <td style='color:#222'>" + (userId != null ? userId : "N/A") + "</td>" +
            "        </tr>" +
            "        <tr>" +
            "          <td style='color:#757575;padding:5px 0;font-weight:600;'>E-mail:</td>" +
            "          <td><a href='mailto:" + userEmail + "' style='color:#00796b;text-decoration:underline'>" + userEmail + "</a></td>" +
            "        </tr>" +
            "        <tr>" +
            "          <td style='color:#757575;padding:5px 0;font-weight:600;'>Assunto:</td>" +
            "          <td style='color:#222;'>" + subject + "</td>" +
            "        </tr>" +
            "      </table>" +
            "      <div style='background:#e0f7fa;padding:17px 20px 15px 20px;border-radius:7px;border-left:7px solid #009688;margin:14px 0 24px 0;font-size:1.07rem;color:#08584e;box-shadow:0 2px 12px #00968812;'>" +
            body +
            "      </div>" +
            "      <div style='text-align:center; margin:32px 0 0 0;'>" +
            "        <a href='https://genfinance.com.br' style='display:inline-block;padding:13px 38px;background:#009688;font-size:1.09rem;font-weight:600;color:#fff;text-decoration:none;border-radius:5px;box-shadow:0 1.5px 9px #00968824;letter-spacing:.5px;transition:.2s;background-image:linear-gradient(90deg,#009688 60%,#00796b);'>Acessar GenFinance</a>" +
            "      </div>" +
            "    </div>" +
            "    <div style='background:#f1f8e9;text-align:center;padding:22px;color:#607d8b;font-size:13px;border-top:1px solid #e0f7fa'>" +
            "      Esta é uma mensagem automática. Não responda este e-mail.<br>GenFinance &copy; " + java.time.Year.now() + " &mdash; Todos os direitos reservados." +
            "    </div>" +
            "  </div>" +
            "</div>";
        sendHtmlMessage(to, "[Feedback] " + subject, html);
    }

    public void sendHtmlMessage(String to, String subject, String html) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true); // true = HTML
            mailSender.send(message);
        } catch (Exception e) {
        	//Faz o L, usuario nao tem que saber de nada nao 
        }
    }
}
