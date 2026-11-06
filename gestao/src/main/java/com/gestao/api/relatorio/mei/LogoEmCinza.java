package com.gestao.api.relatorio.mei;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import javax.imageio.ImageIO;

public final class LogoEmCinza {

    private static final String PREFIXO_DATA_URL = "data:image/";
    private static final String MARCADOR_BASE64 = ";base64,";

    private LogoEmCinza() {}

    public static byte[] converter(String logo) {
        if (logo == null || logo.startsWith(PREFIXO_DATA_URL) == false) {
            return null;
        }
        int inicio = logo.indexOf(MARCADOR_BASE64);
        if (inicio < 0) {
            return null;
        }

        try {
            byte[] original = Base64.getDecoder().decode(logo.substring(inicio + MARCADOR_BASE64.length()));
            BufferedImage imagem = ImageIO.read(new ByteArrayInputStream(original));
            if (imagem == null) {
                return null;
            }

            BufferedImage cinza = new BufferedImage(imagem.getWidth(), imagem.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = cinza.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, imagem.getWidth(), imagem.getHeight());
            g.drawImage(imagem, 0, 0, null);
            g.dispose();

            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            ImageIO.write(cinza, "png", saida);
            return saida.toByteArray();
        } catch (IllegalArgumentException | IOException e) {
            return null;
        }
    }
}
