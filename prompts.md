# Prompts.md — LibroAbierto

## Flujo de trabajo con IA

### Herramientas y modelos
| Herramienta / modelo | Uso |
|---|---|
| Gemini (chat) | Ideación del producto y exploración de alternativas de arquitectura |
| Claude Opus (chat) | Revisión crítica del concepto, recorte del alcance del MVP y redacción de la documentación técnica (Entrega 1) |
| Claude Code (Sonnet 5 / Opus 5.5) | Aplicación del feedback de LIDR, implementación y tests del smart contract (Entrega 2) |

### Skills, subagentes, rules y comandos personalizados
- **[`CLAUDE.md`](CLAUDE.md)** en la raíz del repo: es el contexto fijo que Claude Code lee en cada sesión. Contiene las reglas no negociables de privacidad (nada personal sale de PostgreSQL; el LLM de triage solo ve texto público; la detección de PII corre siempre en local), el hash canónico compartido (RFC 8785 + `keccak256`), la validación de la salida del LLM contra una lista cerrada de artículos, la forma de trabajar (un ticket por vez, tests primero, no dar por terminado nada sin correr los tests) y los comandos de cada carpeta.
- **`readme.md` como fuente de verdad:** los tickets de la sección 6 se pasan a la IA tal cual; sus criterios de aceptación son la especificación de los tests.
- **Memoria persistente de Claude Code:** guarda las fechas de entrega y el criterio de evaluación de LIDR para priorizar entre sesiones.

### Ajustes humanos sobre el output de la IA
Durante la ideación, la propuesta inicial generada con Gemini se revisó y corrigió en estos puntos:
1. **Datos personales fuera de IPFS.** La propuesta original guardaba los datos personales del denunciante cifrados dentro del JSON en IPFS. Se descartó: un dato cifrado sigue siendo dato personal, una filtración futura de la clave lo expondría para siempre y no existe forma de cumplir con el derecho de supresión de la [Ley 25.326 (art. 16)](https://servicios.infoleg.gob.ar/infolegInternet/anexos/60000-64999/64790/texact.htm). Ahora los datos personales viven solo en PostgreSQL y on-chain va únicamente el hash.
2. **No persistir el token de sesión de MiBA**, que la propuesta incluía dentro del payload.
3. **Integraciones estatales simuladas.** MiBA y Trámites a Distancia no exponen APIs abiertas a terceros, así que en el MVP se reemplazan por mocks documentados.
4. **Relayer en lugar de wallet por usuario.** Se descartó account abstraction o Privy para el MVP: agregan complejidad sin aportar al flujo principal.
5. **Moderación y difamación.** Se agregó identidad verificada (simulada) y el derecho a respuesta de la empresa (HU-05).
6. **Recorte de alcance.** Quedaron fuera del MVP el clustering de reclamos, BFA en producción y el dimensionamiento de hardware para inferencia on-premise.
7. **El LLM solo procesa contenido público**, lo que permite usar un proveedor por API en el despliegue sin exponer datos personales.

---

## Índice

1. [Descripción general del producto](#1-descripción-general-del-producto)
2. [Arquitectura del sistema](#2-arquitectura-del-sistema)
3. [Modelo de datos](#3-modelo-de-datos)
4. [Especificación de la API](#4-especificación-de-la-api)
5. [Historias de usuario](#5-historias-de-usuario)
6. [Tickets de trabajo](#6-tickets-de-trabajo)
7. [Pull requests](#7-pull-requests)

---

## 1. Descripción general del producto

**Prompt 1** (Gemini — ideación)
```
Quiero pensar en construir una dapp. Que sea para usar como libro de queja.
```

**Prompt 2** (Gemini — integración con el Estado)
```
Estoy a la vez pensando en integrarlo con defensa del consumidor de CABA.
```

**Prompt 3** (Gemini — análisis de mercado)
```
Hay un proyecto este o estoy creando yo algo?
```

## 2. Arquitectura del Sistema

**Prompt 1** (Gemini — elección de red)
```
Aca la consulta es si sirve usar los Blockchain actuales o es mejor otro tipo?
```

**Prompt 2** (Gemini — IA en el producto)
```
Podemos agregar un llm que ayude en el trabajo del gobierno?
```

**Prompt 3** (Claude — revisión crítica)
```
[Se compartió el PDF de la conversación con Gemini]
Este es el concepto que se me ocurrió
```
Resultado: revisión del diseño (ver "Ajustes humanos"), propuesta de MVP con 4 historias must-have y 2 should-have, y stack.

## 3. Modelo de Datos

**Prompt 1** (Gemini)
```
Ayudame a definir la estructura del JSON y de la base de datos (qué va a IPFS, qué va cifrado, y qué se manda a CABA).
```

**Prompt 2** (Claude)
```
si dale
```
(en respuesta a la propuesta de armar historias de usuario y el esqueleto del Readme). Generó el modelo de datos de la sección 3 del Readme, ya corregido según la revisión anterior.

## 4. Especificación de la API

**Prompt 1** (Claude) — generada en la misma sesión que el modelo de datos.

## 5. Historias de Usuario

**Prompt 1** (Claude) — generadas en la misma sesión, a partir del alcance MVP acordado.

## 6. Tickets de Trabajo

**Prompt 1** (Claude) — generados en la misma sesión.

## 7. Pull Requests

_Pendiente (Entrega 2 y final)._

---

## Ajustes tras la revisión de LIDR (Claude Code — Sonnet)

**Prompt** (resumen): se compartió el PDF de revisión de la Entrega 1 ("luz amarilla") y se pidió aplicar los ajustes al `readme.md`.

Cambios aplicados sobre el diseño existente, sin tocar el alcance del MVP:
1. **Especificación de la API cerrada.** Se agregaron `POST /api/auth/login`, `GET /api/empresas` (búsqueda), `GET /api/quejas/{id}` (detalle), `POST /api/quejas/{id}/evidencias` y `GET /api/panel/quejas` (bandeja del funcionario con triage), que faltaban frente a las pantallas e historias ya descritas.
2. **Paso asíncrono explicitado.** Se agregó el nodo "Worker de publicación" al diagrama de arquitectura y se documentó `content_hash` como la clave con la que el listener correlaciona el evento `ComplaintFiled` (que no trae el UUID interno) con la fila en PostgreSQL.
3. **Tickets reequilibrados.** El ticket de backend que mezclaba hash canónico, IPFS, blockchain gateway y listener se dividió en "Publicación y anclaje asíncrono" y "Listener de eventos on-chain", y se agregaron tickets propios para el **smart contract** y el **servicio de triage**, que no estaban cubiertos.
4. **Coherencia privacidad/LLM.** HU-06 (detección de datos personales antes de publicar) ahora aclara que corre siempre con un modelo local (Ollama), nunca contra el proveedor por API usado para el triage post-publicación, porque en ese punto el texto todavía no es público.
5. **Detalle menor corregido.** Se agregó la columna `telefono_cifrado` al modelo de datos, que la sección de seguridad ya mencionaba pero el ER no tenía.

---

## Implementación — Smart contract `ComplaintRegistry` (Claude Code)

**Prompt**
```
empezá con la implementación del contrato ComplaintRegistry
```
Contexto que usó la IA: el ticket "[Smart Contract] ComplaintRegistry" y el estado on-chain de la sección 3 del readme (enum `Status`, struct `Complaint`, eventos y roles).

Resultado:
- `contracts/src/ComplaintRegistry.sol`: `fileComplaint` (solo `RELAYER_ROLE`), `changeStatus` (solo `OFFICER_ROLE`) con la máquina de estados validada on-chain, errores específicos (`InvalidTransition`, `ComplaintNotFound`) y el comentario del funcionario anclado solo como hash.
- `contracts/test/ComplaintRegistry.t.sol`: 8 tests de Foundry derivados de los criterios de aceptación del ticket (camino feliz, rechazo por rol, transición inválida, estado terminal, queja inexistente).
- `contracts/script/Deploy.s.sol`: despliegue con asignación opcional de roles por variables de entorno.

**Verificación y ajustes humanos**
1. **Instalación de herramientas bajo control.** El instalador de Foundry descarga y ejecuta código externo, y el modo automático de Claude Code lo bloqueó. Mientras tanto la IA validó la compilación con `solc` vía npm (luego lo quitó del proyecto, porque Foundry trae su propio compilador) y pidió permiso; la instalación se hizo recién con autorización explícita.
2. **Correr los tests destapó un bug en los propios tests.** 2 de 8 fallaron: `vm.prank(stranger)` quedaba consumido por la llamada a `registry.RELAYER_ROLE()` dentro de `expectRevert`, así que la llamada protegida la hacía el contrato de test. El contrato estaba bien; el test no. Se corrigió leyendo el rol antes del `prank` y los 8 tests pasan. Esto confirmó la regla de `CLAUDE.md`: nada se da por terminado sin ejecutar la suite.
3. **Warning revisado, no ignorado.** `forge lint` marcó el cast `uint64(block.timestamp)`; se analizó que es seguro y se silenció con un comentario que lo justifica.
4. **Lección para los próximos tickets:** en este ticket los tests y el contrato se escribieron juntos. A partir de aquí, `CLAUDE.md` exige escribir primero los tests a partir de los criterios de aceptación y verlos fallar antes de implementar.
