CREATE TABLE IF NOT EXISTS limites_mei (
    id           BIGSERIAL     PRIMARY KEY,
    ano          INTEGER       NOT NULL UNIQUE,
    valor_limite NUMERIC(12,2) NOT NULL,
    observacao   VARCHAR(255)
);

INSERT INTO limites_mei (ano, valor_limite, observacao) VALUES
    (2026, 81000.00,  NULL),
    (2027, 110000.00, 'LC 238/2026, art. 2º.'),
    (2028, 140000.00, 'LC 238/2026. Valor condicionado à lei orçamentária; pode mudar.')
ON CONFLICT (ano) DO NOTHING;
