package ar.libroabierto.complaint.application;

import ar.libroabierto.complaint.domain.Queja;
import java.time.Instant;
import java.util.UUID;

public interface RegistroDeQuejas {

    /** Alta de una queja nueva; si la empresa no existe, la crea con la razón social de la queja. */
    void registrar(Queja queja);

    long registradasDesde(UUID usuarioId, Instant desde);
}
