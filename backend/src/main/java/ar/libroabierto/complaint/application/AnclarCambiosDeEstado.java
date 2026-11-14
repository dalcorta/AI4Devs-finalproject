package ar.libroabierto.complaint.application;

import ar.libroabierto.anchoring.application.AnclajeDeEstados;
import ar.libroabierto.shared.hashing.Keccak;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Worker outbox de los cambios de estado: los envía al contrato en el orden en que se hicieron. Si uno falla, los
 * siguientes de esa misma queja esperan a la próxima ejecución (el contrato exige el orden); los de otras quejas siguen.
 */
public class AnclarCambiosDeEstado {

    private static final Logger log = LoggerFactory.getLogger(AnclarCambiosDeEstado.class);

    private final CambiosDeEstado cambios;
    private final AnclajeDeEstados cadena;

    public AnclarCambiosDeEstado(CambiosDeEstado cambios, AnclajeDeEstados cadena) {
        this.cambios = cambios;
        this.cadena = cadena;
    }

    public Resultado ejecutar() {
        Set<UUID> quejasDetenidas = new HashSet<>();
        int enviados = 0;
        int fallidos = 0;
        for (CambioPendiente cambio : cambios.pendientesDeAnclaje()) {
            if (quejasDetenidas.contains(cambio.quejaId())) {
                continue;
            }
            // On-chain va solo el hash: el texto del comentario es interno y no se publica.
            String noteHash = cambio.comentario() == null ? Keccak.CERO : Keccak.deTexto(cambio.comentario());
            try {
                cambios.marcarEnviado(cambio.cambioId(),
                        cadena.cambiarEstado(cambio.onchainId(), cambio.nuevo(), noteHash));
                enviados++;
            } catch (RuntimeException e) {
                fallidos++;
                quejasDetenidas.add(cambio.quejaId());
                log.warn("No se pudo anclar el cambio {} de la queja {}; se reintentará", cambio.cambioId(),
                        cambio.quejaId(), e);
            }
        }
        return new Resultado(enviados, fallidos);
    }

    public record Resultado(int enviados, int fallidos) {
    }
}
