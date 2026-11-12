package ar.libroabierto.triage.application;

import ar.libroabierto.triage.domain.Prioridad;
import java.util.List;

/**
 * Salida del LLM ya validada.
 *
 * @param articulos solo los de la lista cerrada
 * @param articulosDescartados los que el LLM citó fuera de la lista: no se guardan, solo se registran
 */
public record SalidaTriage(String categoria, String resumen, Prioridad prioridad, List<String> articulos,
        List<String> articulosDescartados) {
}
