CREATE TABLE IF NOT EXISTS movimentacao_estoque (
    id                BIGSERIAL PRIMARY KEY,
    insumo_id         BIGINT        NOT NULL,
    servico_id        BIGINT        NULL,
    tipo              VARCHAR(20)   NOT NULL,
    quantidade        NUMERIC(12,3) NOT NULL,
    custo_unitario    NUMERIC(12,4) NULL,
    data_movimentacao TIMESTAMP     NOT NULL DEFAULT NOW(),
    observacao        VARCHAR(255)  NULL,
    CONSTRAINT fk_mov_insumo  FOREIGN KEY (insumo_id)  REFERENCES insumo (id),
    CONSTRAINT fk_mov_servico FOREIGN KEY (servico_id) REFERENCES servicos (id)
);

-- Extrato e relatório de giro crescem rápido e filtram por insumo + data.
CREATE INDEX IF NOT EXISTS idx_mov_insumo_data
    ON movimentacao_estoque (insumo_id, data_movimentacao);
