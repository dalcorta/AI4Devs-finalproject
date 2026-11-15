-- HU-05: respuesta pública de la empresa denunciada, una por queja (también lo exige el contrato).
-- Se publica en IPFS y se ancla con fileResponse, como la queja. autor_id es interno: no sale a la API ni a IPFS.
CREATE TABLE respuesta (
    id            uuid        PRIMARY KEY,
    queja_id      uuid        NOT NULL UNIQUE REFERENCES queja (id),
    autor_id      uuid        NOT NULL REFERENCES usuario (id),
    texto         text        NOT NULL CHECK (char_length(texto) BETWEEN 1 AND 3000),
    ipfs_cid      varchar(100),
    -- Clave de correlación con el evento ResponseFiled, como content_hash en queja.
    content_hash  varchar(66) UNIQUE CHECK (content_hash ~ '^0x[0-9a-f]{64}$'),
    tx_hash       varchar(66),
    anchor_status varchar(20) NOT NULL DEFAULT 'PENDIENTE'
        CHECK (anchor_status IN ('PENDIENTE', 'ENVIADA', 'CONFIRMADA', 'FALLIDA')),
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT respuesta_publicada_completa CHECK ((ipfs_cid IS NULL) = (content_hash IS NULL)),
    CONSTRAINT respuesta_enviada_con_hash CHECK (anchor_status = 'PENDIENTE' OR content_hash IS NOT NULL)
);

CREATE INDEX idx_respuesta_pendientes ON respuesta (created_at) WHERE anchor_status = 'PENDIENTE';
