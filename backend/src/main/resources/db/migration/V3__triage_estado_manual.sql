-- HU-03: si la salida del LLM sigue siendo inválida tras 2 reintentos, la queja queda en "Triage manual".
-- Sigue habiendo un solo triage por queja (UNIQUE queja_id): en estado MANUAL no lleva los campos del LLM,
-- pero sí el modelo que falló y cuántos intentos se hicieron, para trazabilidad.
ALTER TABLE triage
    ADD COLUMN estado   varchar(20) NOT NULL DEFAULT 'SUGERIDO' CHECK (estado IN ('SUGERIDO', 'MANUAL')),
    ADD COLUMN intentos int         NOT NULL DEFAULT 1 CHECK (intentos >= 1),
    ALTER COLUMN categoria_sugerida DROP NOT NULL,
    ALTER COLUMN resumen DROP NOT NULL,
    ALTER COLUMN prioridad DROP NOT NULL,
    ALTER COLUMN articulos_sugeridos DROP NOT NULL,
    ADD CONSTRAINT triage_sugerido_completo CHECK (
        estado = 'MANUAL'
        OR (categoria_sugerida IS NOT NULL AND resumen IS NOT NULL AND prioridad IS NOT NULL
            AND articulos_sugeridos IS NOT NULL));
