package ar.libroabierto.triage.application;

import ar.libroabierto.triage.domain.Triage;

public interface TriageRepository {

    /** Guarda el triage si la queja todavía no tiene uno; si ya lo tiene, no hace nada (un triage por queja). */
    void guardar(Triage triage);
}
