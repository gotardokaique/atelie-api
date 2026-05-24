CREATE TABLE IF NOT EXISTS produto (
    id          BIGSERIAL PRIMARY KEY,
    descricao   VARCHAR(255)  NOT NULL,
    preco_venda NUMERIC(12,2) NOT NULL,
    ativo       BOOLEAN       NOT NULL DEFAULT TRUE,
    usuario_id  BIGINT        NOT NULL,
    CONSTRAINT fk_produto_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
);
