package ar.libroabierto.triage.application;

/**
 * Pedido al LLM, independiente del proveedor.
 *
 * @param instrucciones la tarea y sus reglas (va como system prompt)
 * @param contenido la queja, delimitada como dato
 * @param esquemaJson JSON Schema de la salida esperada
 */
public record SolicitudLlm(String instrucciones, String contenido, String esquemaJson) {
}
