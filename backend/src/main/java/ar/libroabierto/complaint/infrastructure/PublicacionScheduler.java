package ar.libroabierto.complaint.infrastructure;

import ar.libroabierto.complaint.application.AnclarCambiosDeEstado;
import ar.libroabierto.complaint.application.PublicarQuejasPendientes;
import ar.libroabierto.complaint.application.PublicarRespuestasPendientes;
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
    private final AnclarCambiosDeEstado cambios;
    private final PublicarRespuestasPendientes respuestas;

    PublicacionScheduler(PublicarQuejasPendientes worker, AnclarCambiosDeEstado cambios,
            PublicarRespuestasPendientes respuestas) {
        this.worker = worker;
        this.cambios = cambios;
        this.respuestas = respuestas;
    }

    // fixedDelay: la próxima pasada empieza cuando termina la anterior, así nunca corren dos a la vez.
    @Scheduled(fixedDelayString = "${libroabierto.worker.intervalo}")
    void publicarPendientes() {
        PublicarQuejasPendientes.Resultado resultado = worker.ejecutar();
        if (resultado.enviadas() > 0 || resultado.fallidas() > 0) {
            log.info("Publicación de quejas: {} enviadas, {} fallidas", resultado.enviadas(), resultado.fallidas());
        }
    }

    // Los cambios de estado del funcionario se anclan con el mismo ritmo que las quejas.
    @Scheduled(fixedDelayString = "${libroabierto.worker.intervalo}")
    void anclarCambiosDeEstado() {
        AnclarCambiosDeEstado.Resultado resultado = cambios.ejecutar();
        if (resultado.enviados() > 0 || resultado.fallidos() > 0) {
            log.info("Cambios de estado: {} enviados, {} fallidos", resultado.enviados(), resultado.fallidos());
        }
    }

    // Las respuestas de las empresas, también con el mismo ritmo.
    @Scheduled(fixedDelayString = "${libroabierto.worker.intervalo}")
    void publicarRespuestas() {
        PublicarRespuestasPendientes.Resultado resultado = respuestas.ejecutar();
        if (resultado.enviadas() > 0 || resultado.fallidas() > 0) {
            log.info("Respuestas de empresas: {} enviadas, {} fallidas", resultado.enviadas(), resultado.fallidas());
        }
    }
}
