-- Esquema inicial de LibroAbierto (readme, sección 3).
-- Los únicos datos personales del sistema viven en la tabla usuario.

CREATE TABLE empresa (
    id           uuid         PRIMARY KEY,
    cuit         varchar(11)  NOT NULL UNIQUE CHECK (cuit ~ '^[0-9]{11}$'),
    razon_social varchar(255) NOT NULL,
    created_at   timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE usuario (
    id                   uuid         PRIMARY KEY,
    email                varchar(255) NOT NULL UNIQUE,
    password_hash        varchar(255) NOT NULL,
    rol                  varchar(20)  NOT NULL CHECK (rol IN ('CIUDADANO', 'FUNCIONARIO', 'EMPRESA')),
    nombre_completo      varchar(255) NOT NULL,
    dni_cifrado          bytea,
    telefono_cifrado     bytea,
    identidad_verificada boolean      NOT NULL DEFAULT false,
    empresa_id           uuid         REFERENCES empresa (id),
    created_at           timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT usuario_empresa_solo_rol_empresa CHECK ((rol = 'EMPRESA') = (empresa_id IS NOT NULL))
);

CREATE TABLE queja (
    id                  uuid          PRIMARY KEY,
    usuario_id          uuid          NOT NULL REFERENCES usuario (id),
    empresa_id          uuid          NOT NULL REFERENCES empresa (id),
    categoria_declarada varchar(100)  NOT NULL,
    descripcion         text          NOT NULL CHECK (char_length(descripcion) <= 5000),
    monto_reclamado     numeric(14, 2) CHECK (monto_reclamado >= 0),
    fecha_hecho         date          NOT NULL,
    estado              varchar(20)   NOT NULL DEFAULT 'REGISTRADA'
        CHECK (estado IN ('REGISTRADA', 'EN_REVISION', 'DERIVADA', 'RESUELTA', 'DESESTIMADA')),
    ipfs_cid            varchar(100),
    -- Clave de correlación con el evento on-chain: única, igual que en el contrato.
    content_hash        varchar(66)   UNIQUE CHECK (content_hash ~ '^0x[0-9a-f]{64}$'),
    onchain_id          bigint        UNIQUE,
    anchor_status       varchar(20)   NOT NULL DEFAULT 'PENDIENTE'
        CHECK (anchor_status IN ('PENDIENTE', 'ENVIADA', 'CONFIRMADA', 'FALLIDA')),
    created_at          timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX idx_queja_empresa_created ON queja (empresa_id, created_at DESC);
CREATE INDEX idx_queja_estado ON queja (estado);
CREATE INDEX idx_queja_pendientes ON queja (created_at) WHERE anchor_status = 'PENDIENTE';

CREATE TABLE evidencia (
    id         uuid         PRIMARY KEY,
    queja_id   uuid         NOT NULL REFERENCES queja (id),
    ipfs_cid   varchar(100) NOT NULL,
    mime_type  varchar(100) NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE triage (
    id                  uuid         PRIMARY KEY,
    queja_id            uuid         NOT NULL UNIQUE REFERENCES queja (id),
    categoria_sugerida  varchar(100) NOT NULL,
    resumen             text         NOT NULL,
    prioridad           varchar(10)  NOT NULL CHECK (prioridad IN ('BAJA', 'MEDIA', 'ALTA')),
    articulos_sugeridos jsonb        NOT NULL,
    modelo              varchar(100) NOT NULL,
    created_at          timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_triage_prioridad ON triage (prioridad);

CREATE TABLE cambio_estado (
    id              uuid        PRIMARY KEY,
    queja_id        uuid        NOT NULL REFERENCES queja (id),
    estado_anterior varchar(20) NOT NULL,
    estado_nuevo    varchar(20) NOT NULL,
    actor_id        uuid        NOT NULL REFERENCES usuario (id),
    comentario      text,
    tx_hash         varchar(66),
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE transaccion_blockchain (
    id            uuid        PRIMARY KEY,
    tx_hash       varchar(66) NOT NULL UNIQUE,
    tipo          varchar(20) NOT NULL CHECK (tipo IN ('REGISTRO', 'CAMBIO_ESTADO', 'RESPUESTA')),
    referencia_id uuid        NOT NULL,
    block_number  bigint,
    estado        varchar(20) NOT NULL CHECK (estado IN ('ENVIADA', 'CONFIRMADA', 'FALLIDA')),
    intentos      int         NOT NULL DEFAULT 0,
    created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE listener_checkpoint (
    contrato      varchar(42) PRIMARY KEY,
    ultimo_bloque bigint      NOT NULL,
    updated_at    timestamptz NOT NULL DEFAULT now()
);
