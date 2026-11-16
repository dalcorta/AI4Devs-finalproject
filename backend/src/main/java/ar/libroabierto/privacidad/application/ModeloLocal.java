package ar.libroabierto.privacidad.application;

/**
 * Puerto hacia el modelo que revisa texto todavía privado. Por la regla 2 de CLAUDE.md, el adaptador corre siempre en
 * la máquina o en la red local (Ollama), nunca contra un proveedor externo.
 */
@FunctionalInterface
public interface ModeloLocal {

    /** Devuelve el texto que generó el modelo; si no responde, lanza {@link ModeloLocalNoDisponibleException}. */
    String completar(SolicitudModeloLocal solicitud);
}
