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

---

## Implementación — Adaptador IPFS y entorno local (Claude Code)

**Prompt**
```
tengo podman
```
(en respuesta a que faltaba un motor de contenedores para IPFS y PostgreSQL).

Resultado:
- `KuboIpfsGateway`: publica el documento por la API HTTP de Kubo, con CID v1 y pin, usando el `HttpClient` de Java (sin dependencias nuevas).
- `KuboIpfsGatewayTest` con Testcontainers sobre Podman: el contenido recuperado es idéntico byte a byte, el mismo contenido da el mismo CID (reintentos inocuos) y un nodo caído falla con excepción. 28 tests del backend en total.
- `compose.yaml` con PostgreSQL 16.15, Kubo v0.43.1 y Anvil 1.9.0. Las versiones de las imágenes se verificaron en los registros antes de fijarlas, y los puertos quedan ligados a `127.0.0.1` porque la API de Kubo permite administrar el nodo.

**Verificación y ajustes humanos**
1. **Un verde falso detectado.** La primera corrida "pasó", pero los 3 tests figuraban como omitidos: con `disabledWithoutDocker`, Testcontainers los salteaba en silencio al no encontrar el socket. Se detectó leyendo el conteo de tests omitidos en el reporte, no el resultado global. Se quitó esa opción: ahora, sin motor de contenedores, el test falla con un error explícito (verificado).
2. **Cambios persistentes en la máquina, decididos por el desarrollador.** El socket de la API de Podman estaba deshabilitado. Activarlo es un servicio de systemd persistente; el modo automático de Claude Code bloqueó que la IA lo hiciera por su cuenta. Para no frenar el trabajo, la IA levantó la API de Podman de forma temporal (se apaga sola) solo para correr los tests, y dejó la activación permanente documentada en `CLAUDE.md` para que la haga el desarrollador.
3. **Límite técnico resuelto en el camino.** El primer intento de socket temporal falló porque la ruta superaba los 108 caracteres que admite un socket Unix; se usó una ruta corta.

---

## Implementación — Persistencia con Flyway y JPA (Claude Code)

**Prompt**
```
Sí, seguir
```

Resultado:
- `V1__init.sql`: el esquema completo de la sección 3 del readme, con `CHECK` para cada enum, validación de formato del CUIT y del `content_hash`, e índice parcial para las quejas pendientes que lee el worker.
- Entidades JPA separadas del dominio y `JpaQuejaRepository`, el adaptador del puerto `QuejaRepository`: lee las pendientes con su empresa y evidencias, persiste el avance del anclaje y registra cada transacción en `transaccion_blockchain` una sola vez.
- `EsquemaTest` (7 tests, criterios del Ticket 1) y `JpaQuejaRepositoryTest` (4 tests), contra PostgreSQL 16.15 en Testcontainers. 39 tests del backend en total.

**Verificación y ajustes humanos**
1. **Una inconsistencia del diseño corregida.** El readme define `content_hash` como clave única de correlación, pero la lista de restricciones del Ticket 1 no lo incluía. Se agregó el `UNIQUE` en la migración y en el readme, con test.
2. **El hash sobrevive a la base de datos, y está probado.** `numeric(14,2)` devuelve `15000.00` donde se guardó `15000`. Un test reconstruye el documento público desde una queja leída de PostgreSQL y exige el hash del vector compartido: pasa porque JCS normaliza los números. Sin la canonicalización, este redondeo de la base cambiaría el hash y la verificación en el navegador fallaría.
3. **Hibernate no toca el esquema.** `ddl-auto: validate` hace que cualquier diferencia entre entidades y migración rompa el arranque en lugar de corregirse en silencio.
4. **Idempotencia comprobada otra vez a propósito.** Se quitó el chequeo de transacción existente: el test que guarda dos veces una queja enviada falló por la restricción única de `tx_hash`; al restaurarlo volvió a pasar.
5. **Pendientes conocidos.** El alta de quejas nuevas queda para el caso de uso `RegistrarQueja` (con sus tests). Del Ticket 1 faltan el cifrado de `dni_cifrado` y `telefono_cifrado` y el seed. Con más de una instancia del worker habrá que tomar las pendientes con `FOR UPDATE SKIP LOCKED`.

---

## Implementación — Wiring Spring y test de aceptación del ticket (Claude Code)

**Prompt**
```
Sí, seguir
```

Resultado:
- `LibroAbiertoProperties` (propiedades tipadas y validadas), `AnclajeConfig` (beans de Web3j, IPFS y el worker) y `PublicacionScheduler` (`@Scheduled` con `fixedDelay`, así dos pasadas nunca se superponen).
- `PublicacionDeQuejaE2ETest`: arranca la aplicación completa contra PostgreSQL y Kubo en Testcontainers y un Anvil con el contrato desplegado. Inserta una queja `PENDIENTE` y espera, sin invocar nada a mano, a que el scheduler la deje `ENVIADA`.
- `AnclajeConfigTest`: sin clave del relayer la aplicación no arranca, y la clave no aparece al imprimir la configuración.
- La preparación de Anvil se extrajo a `AnvilLocal`, compartida por el test del adaptador y el de aceptación. 42 tests del backend en total.

**Verificación y ajustes humanos**
1. **El test de aceptación mide la promesa del producto, no solo el estado.** Además de `ENVIADA`, comprueba que el contenido que quedó en IPFS hashea exactamente al `contentHash` anclado en el contrato (lo que hará el navegador en HU-02) y que el documento no contiene el nombre, el email ni el teléfono del denunciante cargados en la base.
2. **Rojo por el motivo correcto.** Antes del wiring, la aplicación arrancaba pero la queja nunca salía de `PENDIENTE`: el test falló por timeout, no por un error de compilación o de contexto.
3. **Secretos fuera del repositorio, comprobado.** La clave del relayer no tiene valor por defecto en `application.yml`. Se verificó que el test detecta su ausencia quitando a propósito la validación `@NotBlank`: el test falló y, al restaurarla, volvió a pasar. También se descubrió que sin `spring-boot-starter-validation` las anotaciones no se aplican y la aplicación arrancaría sin clave; se agregó la dependencia.
4. **Ticket cerrado contra su criterio de aceptación.** El readme pedía "un test de integración con Anvil donde una queja PENDIENTE termina en ENVIADA con su content_hash persistido y una transacción enviada"; el test de aceptación lo cubre con la aplicación real.

---

## Implementación — Listener de eventos on-chain, Ticket 4 (Claude Code)

**Prompt**
```
Sí, seguir
```

Resultado:
- Dominio: `Queja.confirmarAnclaje(onchainId, txHash, bloque)`. Si la queja ya está confirmada con ese id, devuelve `false` y no cambia nada; si el id es otro, falla. También confirma una queja publicada que quedó `PENDIENTE`, que es lo que pasa si el worker se cae entre enviar la transacción y guardarla. La cadena es la fuente de verdad.
- Caso de uso `ProcesarEventosOnChain`, que funciona así:
  - Lee `ComplaintFiled` por lotes de bloques a partir del checkpoint.
  - Busca cada queja por `content_hash`, la confirma y dispara el triage.
  - Guarda el checkpoint al terminar cada lote.
  - Respeta un número configurable de confirmaciones antes de leer un bloque.
- Puertos `FuenteDeEventos`, `CheckpointRepository` y `DisparadorTriage`.
- Adaptadores:
  - `Web3jFuenteDeEventos`, con `eth_getLogs` sobre rangos acotados.
  - `JpaCheckpointRepository`, un checkpoint por contrato.
  - `ListenerScheduler`.
  - `TriageSinImplementar`, un disparador provisorio que solo deja constancia hasta el Ticket 5.
- El adaptador JPA ahora persiste `onchain_id` y pasa la fila de `transaccion_blockchain` a `CONFIRMADA` con su `block_number`. Si esa fila no existía, porque el worker se cayó, la crea.
- Migración `V2`: la base exige que `onchain_id` exista si y solo si la queja está `CONFIRMADA`, y que no se envíe ni confirme una queja sin `content_hash`.
- `PublicacionDeQuejaE2ETest` ahora cubre el recorrido completo PENDIENTE → ENVIADA → CONFIRMADA con la aplicación real, y agrega el criterio de aceptación del reproceso de eventos.
- 72 tests del backend, 0 fallidos, 0 omitidos.

**Verificación y ajustes humanos**
1. **Polling en vez de suscripción.** El readme hablaba de "suscripción a logs". Se eligió `eth_getLogs` periódico con checkpoint por estos motivos:
   - funciona con cualquier RPC HTTP;
   - no pierde eventos si se corta una conexión WebSocket;
   - con el tope de bloques por lote respeta el límite de rango de los RPC públicos.

   El costo es una latencia de unos segundos, que no importa para una queja. La diferencia con el readme se señaló antes de tocarlo y, con el visto bueno humano, se actualizó el readme (arquitectura, árbol de carpetas y tareas del Ticket 4).
2. **Un test de aceptación que no dependa del azar.** La primera versión del test de reproceso borraba el checkpoint mientras el scheduler corría. Eso es una carrera: el scheduler podía volver a escribir el checkpoint sin haber releído nada, y el test pasaría sin probar lo que dice. Se reemplazó por un listener nuevo sin checkpoint que relee todos los eventos reales de Anvil contra la base real. El test exige que haya eventos ignorados, que la fila quede idéntica y que el triage se haya disparado una sola vez.
3. **Un fake que mentía.** Un test unitario falló: si la base falla al guardar, el siguiente intento no volvía a disparar el triage. El código estaba bien. El problema era el repositorio en memoria del test, que devolvía la misma instancia y "recordaba" una confirmación que nunca se había guardado. Se cambió para que devuelva copias, como hace PostgreSQL.
4. **Garantías comprobadas rompiéndolas a propósito.**
   - Sin el chequeo de idempotencia fallan el test unitario de reproceso y el de aceptación contra Anvil y PostgreSQL.
   - Si el checkpoint se guarda antes de procesar el lote, falla el test que exige reintentar el lote cuando la base no está disponible.
   - En ambos casos, al restaurar el código vuelve a pasar todo.
5. **Un fallo del triage no frena al listener.** La confirmación ya quedó guardada, así que el error se registra en el log y el checkpoint avanza. Como un evento repetido no vuelve a disparar el triage, el Ticket 5 tendrá que recuperar las quejas `CONFIRMADA` que no tengan triage, en lugar de depender solo de este aviso.
6. **Pendientes conocidos.**
   - `StatusChanged` todavía no se procesa, porque el backend aún no tiene el flujo del funcionario que cambia estados.
   - Una transacción minada pero revertida sigue dejando la queja `ENVIADA`; falta pasarla a `FALLIDA`.

---

## Implementación — Triage con LLM, Ticket 5 (Claude Code)

**Prompt**
```
Sí, seguir
```

Antes de escribir código, Claude Code señaló tres decisiones que el readme dejaba abiertas y las preguntó:
- **Proveedor:** se eligió Claude (Anthropic).
- **Dónde guardar el "Triage manual":** se eligió un estado en la tabla `triage`.
- **Cómo llega una queja confirmada al triage:** se eligió un worker outbox en lugar de que el listener lo dispare.

Resultado:
- Dominio `triage`: `Triage` (`SUGERIDO` o `MANUAL`), `Prioridad` y `ArticulosLey24240`, la lista cerrada de artículos con su tema.
- Aplicación:
  - `PromptTriage` arma el pedido con el `DocumentoPublico` de la queja delimitado entre `<queja>` y `</queja>`.
  - `ValidadorSalidaTriage` hace tres cosas:
    - valida contra el JSON Schema `salida-triage.schema.json` con networknt;
    - exige un resumen de 3 líneas como máximo;
    - descarta los artículos que no están en la lista cerrada.
  - `TriarQuejasConfirmadas`, el worker outbox: hace 1 intento más 2 reintentos y, si ninguna salida es válida, guarda un triage `MANUAL`. Si el proveedor no responde, no guarda nada.
  - Puertos `LlmClient`, `QuejasParaTriage` y `TriageRepository`.
- Infraestructura:
  - `AnthropicLlmClient` usa el SDK oficial `anthropic-java` 3.3.0 con `claude-opus-5-5`, salida estructurada (`output_config.format` con el esquema), effort `medium` explícito y el fallback del servidor ante rechazos (`fallbacks: "default"`).
  - `JpaTriageRepository` guarda un triage por queja y conserva el primero.
  - `TriageScheduler` y `TriageConfig`: con el triage habilitado y sin `ANTHROPIC_API_KEY`, la aplicación no arranca.
- Migración `V3`: la tabla `triage` gana `estado` e `intentos`, y los campos del LLM solo pueden ser nulos en `MANUAL`.
- El listener ya no llama al triage: se quitó `DisparadorTriage`.
- El test de aceptación ahora recorre PENDIENTE → ENVIADA → CONFIRMADA → triage, con un LLM simulado. Comprueba que el LLM recibió el CUIT de la empresa y nunca el nombre, el email ni el teléfono del denunciante, y que reprocesar los eventos no genera un segundo triage ni una segunda llamada al LLM.
- 108 tests del backend, 0 fallidos, 0 omitidos.

**Verificación y ajustes humanos**
1. **Las decisiones de diseño se preguntaron.** El readme no decía dónde se guarda el "Triage manual" ni qué proveedor usar. Se preguntó antes de escribir los tests, porque las respuestas cambiaban qué se testeaba. El readme se actualizó con lo elegido.
2. **Una diferencia con el readme, señalada sin cambiarlo.** El readme nombra Spring AI como cliente. Se usó el SDK oficial de Anthropic: el adaptador queda detrás del puerto `LlmClient`, así que cambiarlo solo toca un archivo. Con el visto bueno humano, el readme se actualizó para nombrar el SDK oficial.
3. **Cero costo en los tests, sin perder precisión.** Ningún test llama al proveedor real.
   - `AnthropicLlmClientTest` levanta un servidor HTTP local que imita la Messages API y comprueba el pedido exacto: modelo, instrucciones, contenido delimitado, esquema y fallback.
   - El resto usa un LLM simulado.
   - Antes de escribir el adaptador se verificó que el SDK use Jackson 2, como Spring Boot 3.5, para no repetir el problema que hubo con web3j.
4. **El texto del usuario no puede romper el delimitador.** En el JSON público, `<` y `>` se escriben como `<` y `>`, un escape equivalente: el modelo lee el mismo dato, pero una descripción que contenga `</queja>` no cierra la etiqueta. Hay un test con un intento de inyección.
5. **Un esquema, dos usos.** La salida estructurada de la API no acepta `maxLength`, `pattern` ni `maxItems`. El adaptador manda una versión sin esas restricciones y la validación local aplica el esquema completo. La salida se valida aunque la API haya respetado el esquema: es un dato no confiable.
6. **Garantías comprobadas rompiéndolas a propósito.** Cada una de estas roturas hizo fallar al menos un test, y al restaurar el código volvió a pasar todo:
   - quitar el filtro de la lista cerrada;
   - quitar los reintentos;
   - quitar el escape de los delimitadores;
   - quitar el chequeo de triage existente, que hace saltar el `UNIQUE`;
   - quitar el fallback ante rechazos.
7. **Pendientes conocidos.**
   - **Revisión jurídica de la lista cerrada:** los temas solo orientan al modelo. Se dejaron afuera los artículos cuyo contenido no se pudo confirmar.
   - **Prueba con la API real:** el adaptador no se probó contra el proveedor real, porque cada corrida tiene costo y requiere la clave del usuario.
   - **Sin detección de datos personales:** la HU-06 todavía no existe, así que si un ciudadano escribe datos personales en su propia descripción, esos datos ya son públicos y le llegan al LLM. El prompt pide no repetirlos en el resumen.

---

## Implementación — Registro de quejas por API y login con JWT (Claude Code)

**Prompt**
```
Sí, seguir
```

Resultado:
- `POST /api/quejas` (HU-01) responde `202` con `id`, `anchorStatus: PENDIENTE` y `Location`.
- El caso de uso `RegistrarQueja` hace estas validaciones:
  - el denunciante es un ciudadano con identidad verificada;
  - el CUIT tiene dígito verificador módulo 11 correcto;
  - categoría, descripción y razón social dentro de los largos de la base;
  - el monto no es negativo, tiene hasta 2 decimales y entra en `numeric(14,2)`;
  - la fecha del hecho no es futura.

  Después aplica el límite de 5 quejas por día. Informa todos los campos inválidos juntos (400). La empresa se crea si no existe y, si existe, su razón social no la cambia el denunciante.
- El "día" del límite y de la fecha del hecho es el de Buenos Aires, no el del servidor. Hay tests para las 23:30 hora argentina, que en UTC ya es el día siguiente.
- Módulo `identity`:
  - `POST /api/auth/login` con BCrypt.
  - JWT HS256 que lleva solo el id interno y el rol.
  - Mismo error para un email desconocido que para una clave incorrecta, y en ambos casos se verifica un hash, para no delatar por tiempo de respuesta qué emails existen.
  - `JWT_SECRET` sin valor por defecto y con un mínimo de 32 bytes.
- `SeguridadConfig` niega por defecto, no usa sesiones y no tiene CSRF, porque la credencial va en un header y no en una cookie. El denunciante sale del token firmado, nunca del cuerpo del pedido.
- El test de aceptación de punta a punta ahora empieza como en producción: login y `POST /api/quejas`, luego publicación, anclaje, confirmación y triage.
- 153 tests del backend, 0 fallidos, 0 omitidos.

**Verificación y ajustes humanos**
1. **El alcance incluyó el login porque la especificación lo exige.** El endpoint está protegido con JWT y rol `CIUDADANO`, y sin autenticación no hay forma correcta de saber quién denuncia. Se implementó el mínimo: login, emisión del token y validación del token.
2. **Vectores de CUIT verificados por separado.** Los dígitos verificadores de los tests se calcularon con un script independiente del código. Uno de los vectores propuestos para "el resto da 10" estaba mal y se corrigió antes de usarlo.
3. **Un test que pasaba por la razón equivocada se habría notado.** El test de la API corría dentro de una transacción de test y la fila insertada por JPA todavía no se había volcado a la base cuando se la consultaba por JDBC. Se quitó la transacción del test: cada pedido confirma su transacción como en producción y las tablas se limpian a mano.
4. **Garantías comprobadas rompiéndolas a propósito.** Cada una de estas roturas hizo fallar al menos un test, y al restaurar el código volvió a pasar todo:
   - aceptar cualquier dígito verificador;
   - quitar el control de identidad verificada;
   - correr el límite diario en uno;
   - quitar el hash de relleno del login;
   - abrir el endpoint sin token.
5. **Una diferencia con el readme, señalada sin cambiarlo.** La especificación de `POST /api/quejas` no lista `403`, pero la HU-01 exige un ciudadano verificado. El endpoint responde `403` a un usuario que no es ciudadano o que no tiene la identidad verificada. Con el visto bueno humano, se agregó el `403` a la especificación del readme.
6. **Pendientes conocidos.**
   - El límite diario cuenta antes de insertar, así que dos pedidos simultáneos podrían pasarlo por uno.
   - No hay límite de intentos de login.
   - No hay seed de usuarios: hoy se crean con SQL.
   - Los endpoints públicos de lectura (HU-02) siguen pendientes.
