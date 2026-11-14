package ar.libroabierto.complaint.application;

import ar.libroabierto.complaint.domain.AnchorStatus;
import ar.libroabierto.complaint.domain.CambioDeEstado;
import ar.libroabierto.complaint.domain.EstadoQueja;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CambiosDeEstado {

    Optional<Situacion> situacion(UUID quejaId);

    /**
     * Guarda el cambio y actualiza el estado de la queja solo si sigue en {@code cambio.anterior()}, en una misma
     * transacción.
     *
     * @return {@code false} si otro funcionario la cambió en el medio: no se guarda nada
     */
    boolean registrar(CambioDeEstado cambio);

    /** Cambios sin transacción enviada, en el orden en que se hicieron. */
    List<CambioPendiente> pendientesDeAnclaje();

    void marcarEnviado(UUID cambioId, String txHash);

    record Situacion(Long onchainId, AnchorStatus anclaje, EstadoQueja estado) {
    }
}
