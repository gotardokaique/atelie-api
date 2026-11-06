INSERT INTO limites_mei (ano, valor_limite, observacao)
SELECT ano, 81000.00, NULL
FROM generate_series(2019, 2025) AS ano
ON CONFLICT (ano) DO NOTHING;
