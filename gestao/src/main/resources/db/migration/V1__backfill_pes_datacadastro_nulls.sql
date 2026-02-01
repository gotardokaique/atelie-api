ALTER TABLE pessoas
ADD COLUMN IF NOT EXISTS pes_datacadastro date;

UPDATE pessoas
SET pes_datacadastro = DATE '2026-01-01'
WHERE pes_datacadastro IS NULL;

-- ALTER TABLE pessoas ALTER COLUMN pes_datacadastro SET NOT NULL;
