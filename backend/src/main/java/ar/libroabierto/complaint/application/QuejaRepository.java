package ar.libroabierto.complaint.application;

import ar.libroabierto.complaint.domain.Queja;
import java.util.List;

public interface QuejaRepository {

    List<Queja> pendientesDeAnclaje();

    void guardar(Queja queja);
}
