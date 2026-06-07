-- Foto (avatar) do usuário. TEXT para aceitar URL longa ou data URL base64.
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS usu_foto TEXT;

-- Preferências do usuário (1:1 com usuarios). Cada usuário tem uma linha,
-- criada com os valores default na primeira vez que é solicitada.
DROP TABLE IF EXISTS configuracoes;
CREATE TABLE configuracoes (
    cfg_id                          BIGSERIAL    PRIMARY KEY,
    cfg_usuario_id                  BIGINT       NOT NULL UNIQUE,

    cfg_telefone                    VARCHAR(30),

    cfg_tema                        VARCHAR(20)  NOT NULL DEFAULT 'system',
    cfg_paleta                      VARCHAR(30)  NOT NULL DEFAULT 'default',
    cfg_papel_parede                VARCHAR(60)  NOT NULL DEFAULT 'default',
    cfg_densidade                   VARCHAR(20)  NOT NULL DEFAULT 'confortavel',

    cfg_idioma                      VARCHAR(10)  NOT NULL DEFAULT 'pt-BR',
    cfg_formato_data                VARCHAR(20)  NOT NULL DEFAULT 'dd/MM/yyyy',
    cfg_moeda                       VARCHAR(10)  NOT NULL DEFAULT 'BRL',
    cfg_fuso_horario                VARCHAR(60)  NOT NULL DEFAULT 'America/Sao_Paulo',
    cfg_pagina_inicial              VARCHAR(40)  NOT NULL DEFAULT 'home',

    cfg_notificar_prazo             BOOLEAN      NOT NULL DEFAULT TRUE,
    cfg_dias_antecedencia_prazo     INTEGER      NOT NULL DEFAULT 3,
    cfg_notificar_pagamento         BOOLEAN      NOT NULL DEFAULT TRUE,

    cfg_assistente_ativo            BOOLEAN      NOT NULL DEFAULT FALSE,
    cfg_assistente_voz              VARCHAR(60)  NOT NULL DEFAULT 'EXAVITQu4vr4xnSDxMaL',

    CONSTRAINT fk_cfg_usuario FOREIGN KEY (cfg_usuario_id) REFERENCES usuarios (id)
);
