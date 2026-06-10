package com.gestao.api.bo;

import java.util.Map;

/**
 * Templates HTML dos e-mails do Gestão Ateliê.
 *
 * Placeholders seguem o padrão {@code #nomeDoCampo#}. Use o helper
 * {@link #montar(String, Map)} para aplicar todos os replaces de uma vez:
 *
 * <pre>
 *   String html = TemplateEmailStr.montar(TemplateEmailStr.NOVO_USUARIO_ADMIN, Map.of(
 *       "nome", nome,
 *       "email", email,
 *       "provider", "Google",
 *       "dataHora", "31/12/2026 14:30",
 *       "ano", "2026"
 *   ));
 * </pre>
 *
 * Os templates usam acentos UTF-8 direto — o EmailBO envia com
 * {@code charset=UTF-8} no assunto e no corpo.
 */
public final class TemplateEmailStr {

    private TemplateEmailStr() {}

    /**
     * Substitui todos os placeholders {@code #chave#} pelos valores do mapa.
     * Valores {@code null} viram string vazia (não quebra o HTML).
     */
    public static String montar(String template, Map<String, String> vars) {
        if (template == null) return "";
        if (vars == null || vars.isEmpty()) return template;
        String resultado = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            String valor = e.getValue() == null ? "" : e.getValue();
            resultado = resultado.replace("#" + e.getKey() + "#", valor);
        }
        return resultado;
    }

    // =========================================================================
    // RESET DE SENHA
    // Placeholders: #saudacao# (ex.: ", João" ou ""), #linkResetSenha#, #ano#
    // =========================================================================
    public static final String RESET_SENHA_LINK =
        "<!DOCTYPE html>" +
        "<html lang='pt-BR'><head>" +
        "<meta charset='UTF-8'>" +
        "<meta name='viewport' content='width=device-width,initial-scale=1.0'>" +
        "<title>Redefinição de Senha</title></head>" +
        "<body style='margin:0;padding:0;background-color:#f0ece6;" +
        "font-family:Helvetica Neue,Arial,sans-serif;'>" +

        "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0'" +
        " style='background-color:#f0ece6;padding:48px 16px;'>" +
        "<tr><td align='center'>" +

        "<table role='presentation' cellpadding='0' cellspacing='0' border='0'" +
        " style='width:100%;max-width:560px;background-color:#ffffff;" +
        "border-radius:8px;overflow:hidden;box-shadow:0 4px 32px rgba(0,0,0,0.10);'>" +

        // HEADER
        "<tr><td style='background-color:#111111;padding:38px 48px 34px;text-align:center;'>" +
        "<p style='margin:0 0 8px 0;font-size:10px;letter-spacing:5px;color:#b8956a;" +
        "text-transform:uppercase;font-weight:600;'>Gestão Ateliê</p>" +
        "<h1 style='margin:0;color:#ffffff;font-size:28px;font-weight:300;" +
        "letter-spacing:4px;text-transform:uppercase;'>Gestão Ateliê</h1>" +
        "<div style='width:36px;height:2px;background-color:#b8956a;margin:14px auto 0;'></div>" +
        "</td></tr>" +

        "<tr><td style='height:3px;" +
        "background:linear-gradient(90deg,#6b4e10,#c9a96e,#e8d5a3,#c9a96e,#6b4e10);" +
        "font-size:0;line-height:3px;'>&nbsp;</td></tr>" +

        // BODY
        "<tr><td style='padding:52px 48px 44px;text-align:center;'>" +

        "<h2 style='margin:0 0 22px;color:#111111;font-size:22px;" +
        "font-weight:600;letter-spacing:-0.3px;'>Redefinição de Senha</h2>" +

        "<p style='margin:0 0 6px;color:#333333;font-size:15px;line-height:1.7;'>" +
        "Olá#saudacao#,</p>" +
        "<p style='margin:0 0 36px;color:#666666;font-size:15px;line-height:1.8;'>" +
        "Recebemos uma solicitação de redefinição de senha " +
        "para sua conta.<br>Clique no botão abaixo para criar uma nova senha " +
        "com segurança.</p>" +

        "<a href='#linkResetSenha#'" +
        " style='display:inline-block;background-color:#111111;color:#c9a96e;" +
        "text-decoration:none;padding:16px 52px;border-radius:2px;" +
        "font-size:12px;font-weight:700;letter-spacing:3px;text-transform:uppercase;" +
        "border:1.5px solid #c9a96e;'>Redefinir Minha Senha</a>" +

        "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0'" +
        " style='margin-top:40px;margin-bottom:28px;'><tr>" +
        "<td style='height:1px;background-color:#ede8e0;font-size:0;line-height:1px;'>" +
        "&nbsp;</td></tr></table>" +

        "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0'>" +
        "<tr><td style='background-color:#faf7f3;border-radius:6px;padding:18px 22px;" +
        "border-left:3px solid #c9a96e;text-align:left;'>" +
        "<p style='margin:0 0 5px;color:#111111;font-size:13px;font-weight:700;'>" +
        "⏳&nbsp; Validade do link</p>" +
        "<p style='margin:0;color:#777777;font-size:13px;line-height:1.6;'>" +
        "Este link expira em <strong style='color:#111111;'>5 minutos</strong>. " +
        "Solicite um novo caso ele expire.</p>" +
        "</td></tr>" +
        "<tr><td style='height:12px;'></td></tr>" +

        "<tr><td style='background-color:#faf7f3;border-radius:6px;padding:18px 22px;" +
        "border-left:3px solid #b8956a;text-align:left;'>" +
        "<p style='margin:0 0 5px;color:#111111;font-size:13px;font-weight:700;'>" +
        "🔒&nbsp; Não reconhece esta solicitação?</p>" +
        "<p style='margin:0;color:#777777;font-size:13px;line-height:1.6;'>" +
        "Se você não solicitou a redefinição, ignore este " +
        "e-mail. Sua senha permanece inalterada.</p>" +
        "</td></tr>" +
        "</table>" +

        "</td></tr>" +

        // FOOTER
        "<tr><td style='background-color:#111111;padding:28px 48px;text-align:center;'>" +
        "<p style='margin:0 0 8px;color:#b8956a;font-size:10px;letter-spacing:3px;" +
        "text-transform:uppercase;font-weight:500;'>Gestão Ateliê</p>" +
        "<p style='margin:0;color:#555555;font-size:11px;line-height:1.7;'>" +
        "© #ano# Todos os direitos reservados &nbsp;·&nbsp; " +
        "Não responda este e-mail</p>" +
        "</td></tr>" +

        "</table>" +

        "<p style='margin:22px 0 0;color:#aaaaaa;font-size:11px;text-align:center;'>" +
        "Você recebeu este e-mail porque solicitou a redefinição " +
        "de senha da sua conta.</p>" +

        "</td></tr></table>" +
        "</body></html>";

    // =========================================================================
    // NOVO USUÁRIO (notificação ao admin)
    // Placeholders: #nome#, #email#, #provider#, #dataHora#, #ano#
    // =========================================================================
    public static final String NOVO_USUARIO_ADMIN =
        "<!DOCTYPE html>" +
        "<html lang='pt-BR'><head><meta charset='UTF-8'>" +
        "<meta name='viewport' content='width=device-width,initial-scale=1.0'>" +
        "<title>Novo cadastro</title></head>" +
        "<body style='margin:0;padding:0;background-color:#f0ece6;font-family:Helvetica Neue,Arial,sans-serif;'>" +

        "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0'" +
        " style='background-color:#f0ece6;padding:48px 16px;'>" +
        "<tr><td align='center'>" +

        "<table role='presentation' cellpadding='0' cellspacing='0' border='0'" +
        " style='width:100%;max-width:560px;background-color:#ffffff;" +
        "border-radius:8px;overflow:hidden;box-shadow:0 4px 32px rgba(0,0,0,0.10);'>" +

        // HEADER
        "<tr><td style='background-color:#111111;padding:38px 48px 34px;text-align:center;'>" +
        "<p style='margin:0 0 8px 0;font-size:10px;letter-spacing:5px;color:#b8956a;" +
        "text-transform:uppercase;font-weight:600;'>Gestão Ateliê</p>" +
        "<h1 style='margin:0;color:#ffffff;font-size:24px;font-weight:300;" +
        "letter-spacing:3px;text-transform:uppercase;'>Novo cadastro</h1>" +
        "<div style='width:36px;height:2px;background-color:#b8956a;margin:14px auto 0;'></div>" +
        "</td></tr>" +

        "<tr><td style='height:3px;background:linear-gradient(90deg,#6b4e10,#c9a96e,#e8d5a3,#c9a96e,#6b4e10);" +
        "font-size:0;line-height:3px;'>&nbsp;</td></tr>" +

        // BODY
        "<tr><td style='padding:42px 48px 36px;'>" +
        "<p style='margin:0 0 24px;color:#333333;font-size:15px;line-height:1.7;'>" +
        "Um novo usuário acaba de se cadastrar no sistema.</p>" +

        "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0'" +
        " style='border:1px solid #ede8e0;border-radius:6px;'>" +
        "<tr><td style='padding:14px 18px;border-bottom:1px solid #ede8e0;'>" +
        "<span style='display:block;font-size:11px;color:#999999;text-transform:uppercase;letter-spacing:1.5px;margin-bottom:4px;'>Nome</span>" +
        "<span style='color:#111111;font-size:15px;font-weight:600;'>#nome#</span>" +
        "</td></tr>" +
        "<tr><td style='padding:14px 18px;border-bottom:1px solid #ede8e0;'>" +
        "<span style='display:block;font-size:11px;color:#999999;text-transform:uppercase;letter-spacing:1.5px;margin-bottom:4px;'>E-mail</span>" +
        "<span style='color:#111111;font-size:15px;font-weight:600;'>#email#</span>" +
        "</td></tr>" +
        "<tr><td style='padding:14px 18px;border-bottom:1px solid #ede8e0;'>" +
        "<span style='display:block;font-size:11px;color:#999999;text-transform:uppercase;letter-spacing:1.5px;margin-bottom:4px;'>Forma de cadastro</span>" +
        "<span style='color:#111111;font-size:15px;font-weight:600;'>#provider#</span>" +
        "</td></tr>" +
        "<tr><td style='padding:14px 18px;'>" +
        "<span style='display:block;font-size:11px;color:#999999;text-transform:uppercase;letter-spacing:1.5px;margin-bottom:4px;'>Quando</span>" +
        "<span style='color:#111111;font-size:15px;font-weight:600;'>#dataHora#</span>" +
        "</td></tr>" +
        "</table>" +

        "</td></tr>" +

        // FOOTER
        "<tr><td style='background-color:#111111;padding:24px 48px;text-align:center;'>" +
        "<p style='margin:0 0 6px;color:#b8956a;font-size:10px;letter-spacing:3px;" +
        "text-transform:uppercase;font-weight:500;'>Gestão Ateliê</p>" +
        "<p style='margin:0;color:#555555;font-size:11px;line-height:1.7;'>" +
        "© #ano# Todos os direitos reservados &nbsp;·&nbsp; Notificação automática</p>" +
        "</td></tr>" +

        "</table>" +

        "</td></tr></table>" +
        "</body></html>";
}
