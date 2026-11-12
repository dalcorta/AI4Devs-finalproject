package ar.libroabierto.triage.application;

/** Texto devuelto por el LLM, todavía sin validar, y el modelo que lo generó (para trazabilidad). */
public record RespuestaLlm(String texto, String modelo) {
}
