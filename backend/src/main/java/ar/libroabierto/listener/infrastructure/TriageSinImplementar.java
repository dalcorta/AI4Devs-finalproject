package ar.libroabierto.listener.infrastructure;

import ar.libroabierto.listener.application.DisparadorTriage;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Disparador provisorio hasta que exista el servicio de triage con LLM (Ticket 5): solo deja constancia. */
public class TriageSinImplementar implements DisparadorTriage {

    private static final Logger log = LoggerFactory.getLogger(TriageSinImplementar.class);

    @Override
    public void quejaConfirmada(UUID quejaId) {
        log.info("Queja {} confirmada on-chain; el triage con LLM todavía no está implementado", quejaId);
    }
}
