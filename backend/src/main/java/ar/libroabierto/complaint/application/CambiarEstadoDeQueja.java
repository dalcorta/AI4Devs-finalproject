package ar.libroabierto.complaint.application;

import ar.libroabierto.complaint.domain.AnchorStatus;
import ar.libroabierto.complaint.domain.CambioDeEstado;
import ar.libroabierto.complaint.domain.EstadoQueja;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * HU-04: el funcionario cambia el estado de una queja. El cambio queda en la base al instante (con actor, fecha y
 * comentario) y su anclaje on-chain lo hace un worker, como la publicación (patrón outbox).
 */
public class CambiarEstadoDeQueja {

    static final int MAX_COMENTARIO = 1000;

    private final CambiosDeEstado cambios;
    private final Clock reloj;

    public CambiarEstadoDeQueja(CambiosDeEstado cambios, Clock reloj) {
        this.cambios = cambios;
        this.reloj = reloj;
    }

    public UUID ejecutar(UUID actorId, UUID quejaId, EstadoQueja nuevo, String comentario) {
        String texto = comentario == null || comentario.isBlank() ? null : comentario.strip();
        if (texto != null && texto.length() > MAX_COMENTARIO) {
            throw new DatosInvalidosException(Map.of("comentario", "Supera los " + MAX_COMENTARIO + " caracteres"));
        }

        CambiosDeEstado.Situacion situacion = cambios.situacion(quejaId)
                .orElseThrow(() -> new QuejaNoEncontradaException(quejaId));
        // Sin id on-chain no hay sobre qué anclar el cambio.
        if (situacion.anclaje() != AnchorStatus.CONFIRMADA) {
            throw new TransicionNoPermitidaException("La queja todavía no está anclada en la blockchain");
        }
        if (!situacion.estado().puedePasarA(nuevo)) {
            throw new TransicionNoPermitidaException(
                    "No se puede pasar de " + situacion.estado() + " a " + nuevo);
        }

        CambioDeEstado cambio = new CambioDeEstado(UUID.randomUUID(), quejaId, situacion.estado(), nuevo, actorId,
                texto, reloj.instant());
        if (!cambios.registrar(cambio)) {
            throw new TransicionNoPermitidaException("Otro funcionario cambió el estado de la queja; recargá el panel");
        }
        return cambio.id();
    }
}
