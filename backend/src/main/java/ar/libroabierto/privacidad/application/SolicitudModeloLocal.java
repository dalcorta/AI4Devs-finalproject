package ar.libroabierto.privacidad.application;

/** Pedido al modelo local: instrucciones, el texto delimitado como dato y el JSON Schema de la salida. */
public record SolicitudModeloLocal(String instrucciones, String contenido, String esquema) {
}
