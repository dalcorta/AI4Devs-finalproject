package ar.libroabierto.complaint.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Bandeja de la empresa (HU-05): las quejas contra la empresa que representa el usuario, con su respuesta. */
public interface ConsultasDeEmpresa {

    /** Vacío si el usuario no representa a ninguna empresa. */
    Optional<ConsultasPublicas.EmpresaResumen> empresaDe(UUID usuarioId);

    List<QuejaRecibida> quejasRecibidas(UUID usuarioId, int pagina, int tamanio);

    /**
     * @param anclada si ya se puede responder
     * @param respuesta estado de anclaje de la respuesta, o nulo si todavía no respondió
     */
    record QuejaRecibida(UUID id, Long onchainId, String categoria, LocalDate fechaHecho, String estado,
            boolean anclada, String respuesta) {
    }
}
