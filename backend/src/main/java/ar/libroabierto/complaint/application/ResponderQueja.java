package ar.libroabierto.complaint.application;

import ar.libroabierto.complaint.domain.AnchorStatus;
import ar.libroabierto.privacidad.application.RevisarDatosPersonales;
import ar.libroabierto.privacidad.application.Revision;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * HU-05: la empresa denunciada responde públicamente una queja, una sola vez. La respuesta queda en la base al
 * instante y un worker la publica en IPFS y la ancla, como a las quejas (patrón outbox).
 */
public class ResponderQueja {

    static final int MAX_TEXTO = 3000;

    private final Respuestas respuestas;
    private final RevisarDatosPersonales revisor;
    private final Clock reloj;

    public ResponderQueja(Respuestas respuestas, RevisarDatosPersonales revisor, Clock reloj) {
        this.respuestas = respuestas;
        this.revisor = revisor;
        this.reloj = reloj;
    }

    /** @param confirmarDatosPersonales la empresa ya vio el aviso de HU-06 y quiere publicar el texto tal cual */
    public UUID ejecutar(UUID autorId, UUID quejaId, String texto, boolean confirmarDatosPersonales) {
        String limpio = texto == null ? "" : texto.strip();
        if (limpio.isEmpty()) {
            throw new DatosInvalidosException(Map.of("texto", "Es obligatorio"));
        }
        if (limpio.length() > MAX_TEXTO) {
            throw new DatosInvalidosException(Map.of("texto", "Supera los " + MAX_TEXTO + " caracteres"));
        }

        Respuestas.Situacion situacion = respuestas.situacion(quejaId)
                .orElseThrow(() -> new QuejaNoEncontradaException(quejaId));
        UUID empresaDelAutor = respuestas.empresaDe(autorId).orElse(null);
        if (!Objects.equals(empresaDelAutor, situacion.empresaId())) {
            throw new RespuestaNoPermitidaException();
        }
        // El documento de la respuesta referencia el id on-chain de la queja: sin anclaje no hay a qué atarla.
        if (situacion.anclaje() != AnchorStatus.CONFIRMADA) {
            throw new RespuestaNoDisponibleException("La queja todavía no está anclada en la blockchain");
        }
        if (situacion.respondida()) {
            throw new RespuestaNoDisponibleException("La queja ya tiene una respuesta de la empresa");
        }

        // HU-06, también para las respuestas: la empresa puede incluir datos del consumidor sin darse cuenta.
        if (!confirmarDatosPersonales) {
            Revision revision = revisor.revisar(limpio);
            if (revision.requiereConfirmacion()) {
                throw new DatosPersonalesDetectadosException("texto", revision);
            }
        }

        UUID id = UUID.randomUUID();
        if (!respuestas.registrar(new Respuestas.NuevaRespuesta(id, quejaId, autorId, limpio, reloj.instant()))) {
            throw new RespuestaNoDisponibleException("La queja ya tiene una respuesta de la empresa");
        }
        return id;
    }
}
