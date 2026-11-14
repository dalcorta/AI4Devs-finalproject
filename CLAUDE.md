# CLAUDE.md — LibroAbierto

Libro de quejas de consumo público y verificable: el contenido público de cada queja va a IPFS, su hash se ancla en un smart contract y un LLM la clasifica para el organismo de defensa del consumidor. El diseño completo (arquitectura, modelo de datos, API, historias y tickets) está en `readme.md`; es la fuente de verdad. Si el código y el readme no coinciden, se señala antes de cambiar cualquiera de los dos.

## Reglas no negociables

1. **Ningún dato personal sale de PostgreSQL.** Nombre, DNI, email y teléfono del denunciante nunca van a IPFS, a la blockchain, a logs ni al LLM. IPFS recibe solo el JSON público de la sección 3 del readme; on-chain van hash, CID, CUIT y estado.
2. **El LLM de triage solo ve texto ya público** (después de confirmarse on-chain), así que puede ser un proveedor por API. La detección de datos personales antes de publicar (HU-06) lee texto todavía privado: corre **siempre** con un modelo local, nunca contra un proveedor externo.
3. **Hash canónico idéntico en backend y frontend:** JSON Canonicalization Scheme (RFC 8785) + `keccak256`. Cualquier cambio en la serialización se acompaña de vectores de prueba compartidos entre Java y TypeScript.
4. **La salida del LLM es un dato no confiable:** se valida contra JSON Schema, los artículos de la Ley 24.240 se filtran contra una lista cerrada y el texto del usuario va delimitado como dato en el prompt.
5. **Ningún secreto en el repo:** la clave del relayer y las de cifrado van en variables de entorno (`.env`, ignorado por git).

## Forma de trabajar

- Un ticket de la sección 6 del readme por vez, en orden de dependencia.
- **Tests primero:** escribir los tests a partir de los criterios de aceptación del ticket, verlos fallar y después implementar.
- Una tarea no está terminada hasta que su suite de tests corre y pasa; si no se pudo ejecutar, se dice explícitamente.
- Prioridad del MVP: el recorrido registrar → anclar → verificar de punta a punta con cadena local (Anvil), IPFS local (Kubo) y LLM por API. Respuesta de la empresa, detección de PII, cifrado en reposo, Pinata, testnet pública y E2E quedan para después.
- El dominio usa nombres en español (`Queja`, `Empresa`, `estado`), igual que el readme; el código técnico y las APIs de librerías quedan en inglés.

## Estructura y comandos

| Carpeta | Stack | Estado |
|---|---|---|
| `contracts/` | Solidity 0.8.24, Foundry, OpenZeppelin 5 (vía npm) | Implementado: `ComplaintRegistry` |
| `backend/` | Spring Boot 3.5, Java 21, Maven, arquitectura hexagonal por módulo | Tickets de publicación y anclaje, listener on-chain y triage con LLM completos, más `POST /api/quejas` (`RegistrarQueja`), login con JWT y los endpoints públicos de lectura (HU-02). Siguiente: frontend con verificación en el navegador (Ticket 6) |
| `frontend/` | Next.js 16, React 19, TypeScript 5.9, viem, Vitest | Ticket 6 sin E2E: búsqueda, quejas de una empresa y detalle, con verificación de integridad en el navegador (`lib/verify.ts`) |
| `shared/test-vectors/` | JSON | Vectores del hash canónico, usados por backend y frontend |

Contratos (Foundry está en `~/.foundry/bin`):

```bash
cd contracts && npm install && forge build && forge test -vv
```

Backend:

```bash
cd backend && mvn test
```

Frontend (`tests/chain.test.ts` levanta un Anvil real y despliega el artefacto de `contracts/out/`: correr `forge build` antes):

```bash
cd frontend && npm install && npm test && npm run typecheck && npm run build
```

Los tests de integración usan Testcontainers y **fallan** (no se omiten) si no hay motor de contenedores. Con Podman rootless, el socket de la API tiene que estar activo (`systemctl --user enable --now podman.socket`) y se pasa así:

```bash
cd backend && DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock mvn test
```

Al revisar resultados, mirar también el conteo de tests omitidos (`skipped`), no solo que no haya fallos. Cada contexto de Spring de los tests levanta su propio PostgreSQL; `src/test/resources/spring.properties` limita la caché a 2 contextos para que la suite entre en una máquina de 4 GB.

`Web3jBlockchainGatewayTest` levanta un Anvil real y despliega el artefacto de `contracts/out/`: correr `forge build` antes.

Entorno local (PostgreSQL, Kubo, Anvil, con puertos solo en 127.0.0.1): `podman-compose up -d` o `docker compose up -d` desde la raíz.

Configuración del backend por variables de entorno (ver `application.yml`): `SERVER_PORT` (8081 por defecto: el 8080 es del gateway de Kubo), `DB_URL`, `DB_USER`, `DB_PASSWORD`, `IPFS_API_URL`, `RPC_URL`, `CHAIN_ID`, `CONTRACT_ADDRESS`, `RELAYER_PRIVATE_KEY`, `WORKER_HABILITADO`, `WORKER_INTERVALO`, `LISTENER_HABILITADO`, `LISTENER_INTERVALO`, `LISTENER_BLOQUE_INICIAL`, `LISTENER_CONFIRMACIONES`, `LISTENER_MAX_BLOQUES_POR_LOTE`, `TRIAGE_HABILITADO`, `TRIAGE_INTERVALO`, `TRIAGE_MODELO`, `TRIAGE_LOTE`, `ANTHROPIC_API_KEY`. Con el triage habilitado, sin `ANTHROPIC_API_KEY` la aplicación no arranca (`TRIAGE_HABILITADO=false` para trabajar sin LLM). `JWT_SECRET` (32 bytes o más) tampoco tiene valor por defecto; `JWT_DURACION` es opcional (PT1H). `RELAYER_PRIVATE_KEY` no tiene valor por defecto: sin ella la aplicación no arranca, y `LibroAbiertoProperties` la enmascara al imprimirse. web3j queda en 5.0.2 a propósito (última versión sobre Jackson 2, como Spring Boot 3.5).

Los valores esperados de `shared/test-vectors/canonical-hash.json` no se generan con el código que prueban: la forma canónica se contrasta con la librería `canonicalize` (npm) y el hash se calcula con `cast keccak`.

En el backend, el dominio no conoce Web3j ni el proveedor del LLM: se accede por puertos (`BlockchainGateway`, `LlmClient`) con adaptadores reemplazables. El listener lee eventos con `eth_getLogs` por lotes desde `listener_checkpoint` (polling, no WebSocket) y es idempotente: reprocesar un evento no cambia la queja. El triage es otro worker outbox: toma las quejas `CONFIRMADA` sin triage, le manda al LLM solo `DocumentoPublico` delimitado entre `<queja>` y `</queja>`, valida la salida contra `resources/triage/salida-triage.schema.json` y filtra los artículos contra `ArticulosLey24240`. Ese esquema es la única fuente: al proveedor se le manda una versión sin las restricciones que la API no acepta, y la validación local usa el completo. Los tests del triage nunca llaman al proveedor real: usan un LLM simulado o un servidor HTTP local.

Seguridad de la API: sin sesión, JWT HS256 con `sub` = id interno y `rol`; nada de email ni nombre en el token. Lo que no está permitido explícitamente en `SeguridadConfig` se niega. El denunciante de una queja sale siempre del token, nunca del cuerpo del pedido. `UsuarioEntity` no mapea nombre, DNI ni teléfono: ningún flujo actual los necesita. La lectura pública (`JdbcConsultasPublicas`) usa SQL explícito, columna por columna, para que se vea qué sale: nunca `usuario_id`, `actor_id` ni `comentario`.

Frontend: la verificación no confía en el backend. De la API solo toma `onchainId`; el CID y el hash los lee del contrato, baja el contenido de IPFS con ese CID y recalcula el hash canónico. `NO_VERIFICABLE` (IPFS o la cadena no respondieron) nunca se muestra como `COMPROMETIDA`, y el contenido de una queja se muestra solo si quedó `VERIFICADA`. La API del backend se consume por el mismo origen (rewrite en `next.config.ts`, `BACKEND_URL`); IPFS y la cadena, directo desde el navegador (`NEXT_PUBLIC_*`, ver `frontend/.env.example`).

Persistencia: el esquema lo define solo Flyway (`backend/src/main/resources/db/migration`); Hibernate corre en `ddl-auto: validate`. Las entidades JPA viven en `infrastructure/persistence` y no salen de ahí: el adaptador las traduce al dominio. Un cambio de esquema es una migración nueva, nunca la edición de una ya aplicada.

## Git

- Ramas de trabajo `feature/<tema>`; las ramas entregables siguen `feature/entrega-N-da`.
- `prompts.md` registra cómo se usó la IA en cada fase: actualizarlo al cerrar cada ticket.
