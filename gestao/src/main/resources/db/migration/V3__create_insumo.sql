CREATE TABLE IF NOT EXISTS insumo (
    id              BIGSERIAL PRIMARY KEY,
    descricao       VARCHAR(255)  NOT NULL,
    unidade_medida  VARCHAR(30)   NOT NULL,
    saldo           NUMERIC(12,3) NOT NULL DEFAULT 0,
    estoque_minimo  NUMERIC(12,3) NOT NULL DEFAULT 0,
    custo_medio     NUMERIC(12,4) NOT NULL DEFAULT 0,
    ativo           BOOLEAN       NOT NULL DEFAULT TRUE,
    usuario_id      BIGINT        NOT NULL,
    CONSTRAINT fk_insumo_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
);
