-- Coherencia del anclaje, que el listener y el dominio dan por sentada:
-- una queja tiene onchain_id si y solo si está CONFIRMADA, y no se envía ni confirma sin content_hash.
ALTER TABLE queja
    ADD CONSTRAINT queja_onchain_id_solo_confirmada
        CHECK ((anchor_status = 'CONFIRMADA') = (onchain_id IS NOT NULL)),
    ADD CONSTRAINT queja_anclada_con_content_hash
        CHECK (anchor_status NOT IN ('ENVIADA', 'CONFIRMADA') OR content_hash IS NOT NULL);
