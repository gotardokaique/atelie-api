CREATE TABLE IF NOT EXISTS atelies (
    ate_id                       BIGSERIAL    PRIMARY KEY,
    ate_usuario_id               BIGINT       NOT NULL UNIQUE,
    ate_nome                     VARCHAR(120),
    ate_logo                     TEXT,
    ate_endereco                 VARCHAR(255),
    ate_cnpj_cpf                 VARCHAR(20),
    ate_contato_publico          VARCHAR(120),
    ate_horario_funcionamento    VARCHAR(255),
    CONSTRAINT fk_ate_usuario FOREIGN KEY (ate_usuario_id) REFERENCES usuarios (id)
);
