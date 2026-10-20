# Prompts.md — LibroAbierto

## Flujo de trabajo con IA

### Herramientas y modelos
| Herramienta / modelo | Uso |
|---|---|
| Gemini (chat) | Ideación del producto y exploración de alternativas de arquitectura |
| Claude Opus (chat) | Revisión crítica del concepto, recorte del alcance del MVP y redacción de la documentación técnica (Entrega 1) |
| _[completar]_ | Implementación (p. ej. Claude Code / Cursor con Sonnet) — Entrega 2 |

### Skills, subagentes, rules y comandos personalizados
_[Completar en la Entrega 2: reglas del repo (CLAUDE.md / .cursorrules), comandos, subagentes de testing o revisión.]_

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
