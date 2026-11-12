package ar.libroabierto.triage.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Sugerencia de clasificación de una queja, generada por IA. El funcionario puede ignorarla. */
public record Triage(UUID quejaId, EstadoTriage estado, String categoriaSugerida, String resumen, Prioridad prioridad,
        List<String> articulos, String modelo, int intentos) {

    public Triage {
        Objects.requireNonNull(quejaId);
        Objects.requireNonNull(estado);
        Objects.requireNonNull(modelo);
        articulos = List.copyOf(articulos);
        if (estado == EstadoTriage.SUGERIDO) {
            Objects.requireNonNull(categoriaSugerida);
            Objects.requireNonNull(resumen);
            Objects.requireNonNull(prioridad);
        }
    }

    public static Triage sugerido(UUID quejaId, String categoria, String resumen, Prioridad prioridad,
            List<String> articulos, String modelo, int intentos) {
        return new Triage(quejaId, EstadoTriage.SUGERIDO, categoria, resumen, prioridad, articulos, modelo, intentos);
    }

    /** Ninguna salida del LLM pasó la validación: la queja queda marcada como "Triage manual". */
    public static Triage manual(UUID quejaId, String modelo, int intentos) {
        return new Triage(quejaId, EstadoTriage.MANUAL, null, null, null, List.of(), modelo, intentos);
    }
}
