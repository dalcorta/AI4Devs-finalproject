package ar.libroabierto.triage.infrastructure;

import ar.libroabierto.triage.application.TriarQuejasConfirmadas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "libroabierto.triage.habilitado", havingValue = "true", matchIfMissing = true)
class TriageScheduler {

    private static final Logger log = LoggerFactory.getLogger(TriageScheduler.class);

    private final TriarQuejasConfirmadas worker;

    TriageScheduler(TriarQuejasConfirmadas worker) {
        this.worker = worker;
    }

    // fixedDelay: una sola pasada a la vez, así dos pasadas nunca piden el triage de la misma queja.
    @Scheduled(fixedDelayString = "${libroabierto.triage.intervalo}")
    void triarConfirmadas() {
        TriarQuejasConfirmadas.Resultado resultado = worker.ejecutar();
        if (resultado.sugeridos() > 0 || resultado.manuales() > 0 || resultado.pendientes() > 0) {
            log.info("Triage: {} sugeridos, {} a triage manual, {} pendientes por LLM no disponible",
                    resultado.sugeridos(), resultado.manuales(), resultado.pendientes());
        }
    }
}
