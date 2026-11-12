package ar.libroabierto.triage.application;

import ar.libroabierto.complaint.domain.Queja;
import java.util.List;

public interface QuejasParaTriage {

    /** Quejas CONFIRMADA on-chain (su contenido ya es público) que todavía no tienen triage, las más antiguas primero. */
    List<Queja> confirmadasSinTriage(int limite);
}
