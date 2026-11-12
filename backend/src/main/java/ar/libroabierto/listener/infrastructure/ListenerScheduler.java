package ar.libroabierto.listener.infrastructure;

import ar.libroabierto.listener.application.ProcesarEventosOnChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "libroabierto.listener.habilitado", havingValue = "true", matchIfMissing = true)
class ListenerScheduler {

    private static final Logger log = LoggerFactory.getLogger(ListenerScheduler.class);

    private final ProcesarEventosOnChain listener;

    ListenerScheduler(ProcesarEventosOnChain listener) {
        this.listener = listener;
    }

    // fixedDelay: una pasada no empieza hasta que termina la anterior, así el checkpoint avanza en orden.
    @Scheduled(fixedDelayString = "${libroabierto.listener.intervalo}")
    void procesarEventos() {
        ProcesarEventosOnChain.Resultado resultado = listener.ejecutar();
        if (resultado.confirmadas() > 0 || resultado.ignorados() > 0) {
            log.info("Listener: {} quejas confirmadas, {} eventos ignorados, checkpoint en el bloque {}",
                    resultado.confirmadas(), resultado.ignorados(), resultado.hastaBloque());
        }
    }
}
