CREATE TABLE IF NOT EXISTS dispositivos_push (
    dpu_id                  BIGSERIAL     PRIMARY KEY,
    dpu_usuario_id          BIGINT        NOT NULL,
    dpu_expo_push_token     VARCHAR(255)  NOT NULL,
    dpu_plataforma          VARCHAR(20)   NOT NULL,
    dpu_ativo               BOOLEAN       NOT NULL DEFAULT TRUE,
    dpu_criado_em           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    dpu_ultimo_uso_em       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_dpu_expo_push_token UNIQUE (dpu_expo_push_token),
    CONSTRAINT fk_dpu_usuario FOREIGN KEY (dpu_usuario_id) REFERENCES usuarios (id)
);

CREATE INDEX IF NOT EXISTS idx_dpu_usuario_ativo
    ON dispositivos_push (dpu_usuario_id) WHERE dpu_ativo;

-- ntf_visualizada_em NULL = conta no sininho; ntf_lida_em NULL = destacada na lista.
-- ntf_chave_dedupe garante que rodar o job duas vezes não duplica nada.
CREATE TABLE IF NOT EXISTS notificacoes (
    ntf_id                  BIGSERIAL     PRIMARY KEY,
    ntf_usuario_id          BIGINT        NOT NULL,
    ntf_tipo                VARCHAR(30)   NOT NULL,
    ntf_titulo              VARCHAR(120)  NOT NULL,
    ntf_descricao           VARCHAR(255),
    ntf_servico_id          BIGINT,
    ntf_data_referencia     DATE,
    ntf_quantidade          INTEGER       NOT NULL DEFAULT 1,
    ntf_criada_em           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    ntf_push_enviado_em     TIMESTAMPTZ,
    ntf_visualizada_em      TIMESTAMPTZ,
    ntf_lida_em             TIMESTAMPTZ,
    ntf_chave_dedupe        VARCHAR(150)  NOT NULL,
    CONSTRAINT uk_ntf_chave_dedupe UNIQUE (ntf_chave_dedupe),
    CONSTRAINT fk_ntf_usuario FOREIGN KEY (ntf_usuario_id) REFERENCES usuarios (id),
    CONSTRAINT fk_ntf_servico FOREIGN KEY (ntf_servico_id) REFERENCES servicos (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_ntf_usuario_criada
    ON notificacoes (ntf_usuario_id, ntf_criada_em DESC);

CREATE INDEX IF NOT EXISTS idx_ntf_usuario_nao_visualizada
    ON notificacoes (ntf_usuario_id) WHERE ntf_visualizada_em IS NULL;
