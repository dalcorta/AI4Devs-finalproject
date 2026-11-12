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

---

## Implementación — Hash canónico compartido (Claude Code)

**Prompt**
```
Sí, seguir
```
(en respuesta a la propuesta de continuar con el ticket "[Backend] Publicación y anclaje asíncrono", empezando por los tests del hash canónico).

Resultado:
- `shared/test-vectors/canonical-hash.json`: 5 vectores (documento público de ejemplo, orden de claves y espacios, unicode y escapes, formato de números, arrays) con su forma canónica RFC 8785 y su `keccak256`. Los usarán los tests del backend y del frontend.
- `backend/`: esqueleto Spring Boot 3.5 / Java 21 y `CanonicalHasher` (JCS con `java-json-canonicalization` + `keccak256` con web3j).
- `CanonicalHasherTest`: tests parametrizados que leen los vectores compartidos, más formato `bytes32` y rechazo de JSON inválido. 12 tests pasan.

**Verificación y ajustes humanos**
1. **Vectores con oráculos independientes.** Para no generar los valores esperados con el mismo código que se prueba, la IA escribió a mano la forma canónica de cada vector, la contrastó con la librería `canonicalize` de npm (la que usará el frontend) y calculó cada hash con `cast keccak` de Foundry. Los 5 coincidieron.
2. **Versiones consultadas, no inventadas.** Las versiones de Spring Boot, web3j y la librería JCS se leyeron de Maven Central antes de escribir el `pom.xml`; se eligió Spring Boot 3.5.16 porque el readme fija la serie 3 aunque ya existe la 4.
3. **Tests primero, con la herramienta como límite.** Los tests se escribieron antes que `CanonicalHasher`, pero JDK y Maven no estaban instalados y su instalación pedía `sudo`, que la IA no puede usar: el desarrollador los instaló. Como no se pudo ver la fase roja antes de implementar, se hizo después a propósito: se rompió el hash (sin canonicalizar) y los 5 tests de hash fallaron; al restaurarlo, los 12 volvieron a pasar.

---

## Implementación — Worker de publicación (Claude Code)

**Prompt**
```
Commitear y seguir
```

Resultado (dominio y aplicación del módulo `complaint`, sin infraestructura todavía):
- `Queja` con su `AnchorStatus` (`PENDIENTE → ENVIADA`) y `DocumentoPublico`, que arma el JSON de IPFS desde una lista explícita de campos.
- Puertos `QuejaRepository`, `IpfsGateway` y `BlockchainGateway` (este último en el módulo `anchoring`, como fija el readme).
- `PublicarQuejasPendientes`, el worker del patrón outbox: publica en IPFS, persiste `ipfs_cid` y `content_hash`, envía la transacción y marca `ENVIADA`.
- 10 tests nuevos (22 en total, todos pasan): 4 del documento público y 6 del worker con dobles en memoria de IPFS, la cadena y el repositorio.

**Verificación y ajustes humanos**
1. **Tests en rojo antes de implementar.** Esta vez sí: los tests se escribieron primero y fallaron por no compilar (no existían las clases); recién después se implementó.
2. **Privacidad comprobada por test, no por convención.** El documento se arma con una lista blanca de campos y un test verifica las claves exactas del JSON y que el id del denunciante no aparezca. Otro test usa el ejemplo del readme y exige el mismo hash que el vector compartido, así el documento real queda atado a lo que verificará el frontend.
3. **Determinismo en reintentos.** `registrada_en` se trunca a segundos: si no, un reintento podría producir otro JSON y otro hash para la misma queja. El test lo cubre construyendo la queja con microsegundos.
4. **La decisión clave del diseño, protegida por un test.** El `content_hash` se persiste *antes* de enviar la transacción, porque es la clave con la que el listener encontrará la queja. Para confirmar que el test protege esa decisión, se quitó a propósito ese guardado: el test falló, y al restaurarlo volvió a pasar.
5. **Infraestructura ausente, diseño hexagonal aprovechado.** No hay Docker, PostgreSQL ni IPFS en la máquina. Gracias a los puertos, la lógica del worker se probó entera con dobles; los adaptadores reales quedan para el paso siguiente.
6. **Riesgo identificado, pendiente de resolver.** Si la transacción sale pero falla el guardado de `ENVIADA`, el reintento anclaría la misma queja dos veces. La mitigación propuesta es que el contrato rechace un `contentHash` ya registrado y que el adaptador Web3j trate ese rechazo como éxito.

---

## Implementación — Anclaje idempotente con Web3j (Claude Code)

**Prompt**
```
Sí, seguir
```
(en respuesta a la propuesta de resolver el riesgo de doble anclaje junto con el adaptador Web3j contra Anvil).

Resultado:
- **Contrato:** `contentHash` único on-chain (`ComplaintAlreadyFiled`) y la consulta `complaintIdByHash`. 2 tests nuevos de Foundry, escritos antes que el código; 10 en total.
- **Backend:** `Web3jBlockchainGateway`, el relayer. Antes de enviar consulta si el hash ya está anclado y, si lo está, devuelve la transacción original buscando el evento `ComplaintFiled`. Estima el gas antes de enviar, así una transacción que revertiría falla sin gastar gas ni nonce.
- **Test de integración contra Anvil real:** levanta Anvil, despliega el contrato compilado por Forge, otorga `RELAYER_ROLE` y prueba el registro, el reintento idempotente y el rechazo sin rol. 25 tests del backend en total, todos pasan.

**Verificación y ajustes humanos**
1. **Tests primero, en las dos capas.** Los tests del contrato y el de integración se escribieron antes y se vieron fallar por no compilar.
2. **Conflicto de dependencias resuelto con criterio, no forzado.** web3j 6.1.0 trae Jackson 3 y rompió en ejecución (`NoClassDefFound` de una anotación de Jackson 2.22 que Spring Boot 3.5 no trae). En lugar de forzar versiones y convivir con dos majors de Jackson, se revisaron los POM publicados y se bajó a web3j 5.0.2, la última sobre Jackson 2, coherente con el resto del stack.
3. **Defensa en profundidad comprobada.** Para verificar que el test protege la idempotencia se desactivó a propósito el chequeo del adaptador: el test falló, y el error mostró que el contrato rechazaba igual el duplicado con `ComplaintAlreadyFiled`. Hay dos barreras independientes contra el doble anclaje.
4. **Claves de prueba acotadas.** El test usa las claves de desarrollo públicas que genera Anvil, que solo valen en esa cadena local; la clave real del relayer irá por variable de entorno, como fija `CLAUDE.md`.
5. **Pendientes conocidos.** Si la transacción se mina pero revierte, la queja queda `ENVIADA` sin confirmación; se resolverá con la tabla `transaccion_blockchain` y el estado `FALLIDA`. La búsqueda del evento recorre desde el bloque 0, lo que en una testnet pública necesitará acotarse.
