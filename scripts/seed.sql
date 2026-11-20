-- Datos de prueba para desarrollo local (Ticket 1): 3 empresas ficticias y un usuario de cada rol.
-- Todo es ficticio: CUITs inventados (con dígito verificador válido), emails en el dominio reservado .test y sin
-- DNI ni teléfono. NO usar contra una base de producción.
--
-- Uso, con los servicios de compose levantados y el backend arrancado al menos una vez (Flyway crea el esquema):
--   docker compose exec -T postgres psql -U libroabierto -d libroabierto -v ON_ERROR_STOP=1 < scripts/seed.sql
--   (con Podman: podman-compose exec -T postgres psql ... con los mismos argumentos)
--
-- Se puede correr más de una vez: lo que ya existe (mismo CUIT o mismo email) no se toca.
-- Las quejas no se siembran: tienen que pasar por la API para publicarse en IPFS y anclarse en la cadena.
--
-- Credenciales (solo para este entorno local; las claves se guardan como hash BCrypt):
--   ciudadana@libroabierto.test    clave: 0-_9WSm15sL65eUD  (CIUDADANO, identidad verificada: registra quejas)
--   funcionaria@libroabierto.test  clave: VOqMnlLuMzEAsemQ  (FUNCIONARIO: panel /panel)
--   empresa@libroabierto.test      clave: oIIUneVBpJYICiDN  (EMPRESA, representa a Telefonía Ficticia del Plata S.A.: panel /empresa)

INSERT INTO empresa (id, cuit, razon_social) VALUES
    (gen_random_uuid(), '30711111111', 'Telefonía Ficticia del Plata S.A.'),
    (gen_random_uuid(), '30722222225', 'Banco Imaginario Argentino S.A.'),
    (gen_random_uuid(), '30733333339', 'Supermercados Inventados S.R.L.')
ON CONFLICT (cuit) DO NOTHING;

INSERT INTO usuario (id, email, password_hash, rol, nombre_completo, identidad_verificada, empresa_id)
SELECT gen_random_uuid(), u.email, u.password_hash, u.rol, u.nombre_completo, u.identidad_verificada, e.id
FROM (VALUES
    ('ciudadana@libroabierto.test', '$2b$10$zRckZP4Mdc.hgh0MbO4vB.x1Ne5yX5ibRz2idulhR.bAWow6uWf2y', 'CIUDADANO', 'Ana Ficticia', true, NULL),
    ('funcionaria@libroabierto.test', '$2b$10$qXjqqTb/QiZdDrgXX6.Sl.XBcIpqBCeGj8YNxhUszVxQdliNE3oNK', 'FUNCIONARIO', 'Beatriz Ficticia', false, NULL),
    ('empresa@libroabierto.test', '$2b$10$WRwXP3p219u6S3UTq9aigOeUDDu4L3BduldkXSE1hkpLTDJm2aylu', 'EMPRESA', 'Carlos Ficticio', false, '30711111111')
) AS u (email, password_hash, rol, nombre_completo, identidad_verificada, cuit_empresa)
LEFT JOIN empresa e ON e.cuit = u.cuit_empresa
ON CONFLICT (email) DO NOTHING;
