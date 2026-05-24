CREATE TABLE IF NOT EXISTS servico_produto (
    id                   BIGSERIAL PRIMARY KEY,
    servico_id           BIGINT        NOT NULL,
    produto_id           BIGINT        NOT NULL,
    quantidade           NUMERIC(12,3) NOT NULL DEFAULT 1,
    preco_venda_snapshot NUMERIC(12,2) NOT NULL,
    CONSTRAINT fk_servprod_servico FOREIGN KEY (servico_id) REFERENCES servicos (id),
    CONSTRAINT fk_servprod_produto FOREIGN KEY (produto_id) REFERENCES produto (id)
);
