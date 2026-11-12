package ar.libroabierto.complaint.infrastructure;

import ar.libroabierto.complaint.application.PublicarQuejasPendientes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "libroabierto.worker.habilitado", havingValue = "true", matchIfMissing = true)
class PublicacionScheduler {

    private static final Logger log = LoggerFactory.getLogger(PublicacionScheduler.class);

    private final PublicarQuejasPendientes worker;

    PublicacionScheduler(PublicarQuejasPendientes worker) {
        this.worker = worker;
    }

    // fixedDelay: la próxima pasada empieza cuando termina la anterior, así nunca corren dos a la vez.
    @Scheduled(fixedDelayString = "${libroabierto.worker.intervalo}")
    void publicarPendientes() {
        PublicarQuejasPendientes.Resultado resultado = worker.ejecutar();
        if (resultado.enviadas() > 0 || resultado.fallidas() > 0) {
            log.info("Publicación de quejas: {} enviadas, {} fallidas", resultado.enviadas(), resultado.fallidas());
        }
    }
}
