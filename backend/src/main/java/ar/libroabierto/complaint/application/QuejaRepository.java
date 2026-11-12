package ar.libroabierto.complaint.application;

import ar.libroabierto.complaint.domain.Queja;
import java.util.List;
import java.util.Optional;

public interface QuejaRepository {

    List<Queja> pendientesDeAnclaje();

    /** Busca por la clave de correlación con el evento on-chain. */
    Optional<Queja> porContentHash(String contentHash);

    void guardar(Queja queja);
}
