package ar.libroabierto.complaint.application;

import ar.libroabierto.complaint.domain.AnchorStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistencia de las respuestas de las empresas (HU-05). */
public interface Respuestas {

    /** Empresa que representa el usuario, si tiene el rol EMPRESA. */
    Optional<UUID> empresaDe(UUID usuarioId);

    Optional<Situacion> situacion(UUID quejaId);

    /** @return {@code false} si la queja ya tenía una respuesta (otra se guardó en el medio): no se guarda nada */
    boolean registrar(NuevaRespuesta respuesta);

    /** Respuestas sin transacción enviada, de quejas ya ancladas, en el orden en que se hicieron. */
    List<RespuestaPendiente> pendientesDeAnclaje();

    /** Guarda CID y hash antes de enviar la transacción: el listener encuentra la respuesta por ese hash. */
    void registrarPublicacion(UUID respuestaId, String ipfsCid, String contentHash);

    void marcarEnviada(UUID respuestaId, String txHash);

    /** @param empresaId empresa denunciada en la queja */
    record Situacion(UUID empresaId, AnchorStatus anclaje, boolean respondida) {
    }

    record NuevaRespuesta(UUID id, UUID quejaId, UUID autorId, String texto, Instant creadaEn) {
    }
}
