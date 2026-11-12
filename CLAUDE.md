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
| `backend/` | Spring Boot 3.5, Java 21, Maven, arquitectura hexagonal por módulo | En curso: hash canónico, worker de publicación, adaptadores Web3j e IPFS (Kubo); faltan persistencia y wiring Spring |
| `frontend/` | Next.js, TypeScript, viem | Pendiente |
| `shared/test-vectors/` | JSON | Vectores del hash canónico, usados por backend y frontend |

Contratos (Foundry está en `~/.foundry/bin`):

```bash
cd contracts && npm install && forge build && forge test -vv
```

Backend:

```bash
cd backend && mvn test
```

Los tests de integración usan Testcontainers y **fallan** (no se omiten) si no hay motor de contenedores. Con Podman rootless, el socket de la API tiene que estar activo (`systemctl --user enable --now podman.socket`) y se pasa así:

```bash
cd backend && DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock mvn test
```

Al revisar resultados, mirar también el conteo de tests omitidos (`skipped`), no solo que no haya fallos.

`Web3jBlockchainGatewayTest` levanta un Anvil real y despliega el artefacto de `contracts/out/`: correr `forge build` antes.

Entorno local (PostgreSQL, Kubo, Anvil, con puertos solo en 127.0.0.1): `podman-compose up -d` o `docker compose up -d` desde la raíz. web3j queda en 5.0.2 a propósito (última versión sobre Jackson 2, como Spring Boot 3.5).

Los valores esperados de `shared/test-vectors/canonical-hash.json` no se generan con el código que prueban: la forma canónica se contrasta con la librería `canonicalize` (npm) y el hash se calcula con `cast keccak`.

En el backend, el dominio no conoce Web3j ni el proveedor del LLM: se accede por puertos (`BlockchainGateway`, `LlmClient`) con adaptadores reemplazables.

## Git

- Ramas de trabajo `feature/<tema>`; las ramas entregables siguen `feature/entrega-N-da`.
- `prompts.md` registra cómo se usó la IA en cada fase: actualizarlo al cerrar cada ticket.
