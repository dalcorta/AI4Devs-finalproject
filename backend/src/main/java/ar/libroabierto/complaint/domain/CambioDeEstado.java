package ar.libroabierto.complaint.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Un cambio de estado hecho por un funcionario. El comentario es interno: no se publica ni sale a la API pública. */
public record CambioDeEstado(UUID id, UUID quejaId, EstadoQueja anterior, EstadoQueja nuevo, UUID actorId,
        String comentario, Instant registradoEn) {

    public CambioDeEstado {
        Objects.requireNonNull(id);
        Objects.requireNonNull(quejaId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(registradoEn);
        if (!anterior.puedePasarA(nuevo)) {
            throw new IllegalArgumentException("Transición no permitida: " + anterior + " -> " + nuevo);
        }
    }
}
