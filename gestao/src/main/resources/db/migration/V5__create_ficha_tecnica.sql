CREATE TABLE IF NOT EXISTS ficha_tecnica (
    id          BIGSERIAL PRIMARY KEY,
    produto_id  BIGINT        NOT NULL,
    insumo_id   BIGINT        NOT NULL,
    quantidade  NUMERIC(12,3) NOT NULL,
    CONSTRAINT fk_ficha_produto FOREIGN KEY (produto_id) REFERENCES produto (id),
    CONSTRAINT fk_ficha_insumo  FOREIGN KEY (insumo_id)  REFERENCES insumo (id),
    CONSTRAINT uk_ficha_produto_insumo UNIQUE (produto_id, insumo_id)
);
