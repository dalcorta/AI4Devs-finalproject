package ar.libroabierto.privacidad.application;

import ar.libroabierto.privacidad.domain.Hallazgo;
import java.util.List;

/**
 * Resultado de revisar un texto antes de publicarlo.
 *
 * @param hallazgos posibles datos personales, ordenados por posición
 * @param completa {@code false} si el modelo local no pudo buscar nombres: solo se revisaron los patrones
 */
public record Revision(List<Hallazgo> hallazgos, boolean completa) {

    public Revision {
        hallazgos = List.copyOf(hallazgos);
    }

    /** Hay que pedirle al autor que confirme: hay algo para revisar o no se pudo revisar todo. */
    public boolean requiereConfirmacion() {
        return !hallazgos.isEmpty() || !completa;
    }
}
