package ar.libroabierto.listener.application;

import ar.libroabierto.complaint.application.QuejaRepository;
import ar.libroabierto.complaint.domain.Queja;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener de {@code ComplaintFiled}: lee los eventos por lotes de bloques desde el último checkpoint, confirma cada
 * queja buscándola por content_hash y dispara su triage.
 *
 * <p>El checkpoint se guarda después de procesar cada lote. Si algo falla a mitad de un lote, el lote entero se
 * vuelve a leer en la próxima ejecución; es seguro porque confirmar una queja ya confirmada no cambia nada.
 */
public class ProcesarEventosOnChain {

    private static final Logger log = LoggerFactory.getLogger(ProcesarEventosOnChain.class);

    private final QuejaRepository quejas;
    private final FuenteDeEventos cadena;
    private final CheckpointRepository checkpoint;
    private final DisparadorTriage triage;
    private final long bloqueInicial;
    private final int confirmaciones;
    private final int maxBloquesPorLote;

    public ProcesarEventosOnChain(QuejaRepository quejas, FuenteDeEventos cadena, CheckpointRepository checkpoint,
            DisparadorTriage triage, long bloqueInicial, int confirmaciones, int maxBloquesPorLote) {
        if (bloqueInicial < 0 || confirmaciones < 0 || maxBloquesPorLote < 1) {
            throw new IllegalArgumentException("Configuración del listener inválida");
        }
        this.quejas = quejas;
        this.cadena = cadena;
        this.checkpoint = checkpoint;
        this.triage = triage;
        this.bloqueInicial = bloqueInicial;
        this.confirmaciones = confirmaciones;
        this.maxBloquesPorLote = maxBloquesPorLote;
    }

    public Resultado ejecutar() {
        // Solo se leen bloques con suficientes confirmaciones encima, para no procesar eventos de un bloque
        // que una reorganización todavía puede descartar.
        long ultimoSeguro = cadena.bloqueActual() - confirmaciones;
        long procesado = checkpoint.ultimoBloque().orElse(bloqueInicial - 1);
        int confirmadas = 0;
        int ignorados = 0;

        while (procesado < ultimoSeguro) {
            long desde = procesado + 1;
            long hasta = Math.min(ultimoSeguro, desde + maxBloquesPorLote - 1);
            for (EventoQuejaRegistrada evento : cadena.quejasRegistradas(desde, hasta)) {
                if (aplicar(evento)) {
                    confirmadas++;
                } else {
                    ignorados++;
                }
            }
            checkpoint.guardar(hasta);
            procesado = hasta;
        }
        return new Resultado(confirmadas, ignorados, procesado);
    }

    private boolean aplicar(EventoQuejaRegistrada evento) {
        Optional<Queja> encontrada = quejas.porContentHash(evento.contentHash());
        if (encontrada.isEmpty()) {
            // Puede ser un anclaje hecho por otra instancia del sistema sobre el mismo contrato.
            log.warn("Evento ComplaintFiled {} sin queja local para el hash {}", evento.onchainId(),
                    evento.contentHash());
            return false;
        }
        Queja queja = encontrada.get();
        if (!queja.confirmarAnclaje(evento.onchainId(), evento.txHash(), evento.bloque())) {
            return false;
        }
        quejas.guardar(queja);
        try {
            triage.quejaConfirmada(queja.id());
        } catch (RuntimeException e) {
            // La confirmación ya quedó guardada: un fallo del triage no puede frenar el listener ni el checkpoint.
            log.error("No se pudo disparar el triage de la queja {}", queja.id(), e);
        }
        return true;
    }

    /**
     * @param hastaBloque último bloque procesado (el checkpoint) al terminar la ejecución
     */
    public record Resultado(int confirmadas, int ignorados, long hastaBloque) {
    }
}
