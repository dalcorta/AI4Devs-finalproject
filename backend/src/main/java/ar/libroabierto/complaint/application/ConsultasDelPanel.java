package ar.libroabierto.complaint.application;

import ar.libroabierto.complaint.domain.EstadoQueja;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Bandeja del funcionario: solo quejas ancladas (sin id on-chain no hay estado que cambiar). */
public interface ConsultasDelPanel {

    /**
     * Primero las de triage manual (nadie las clasificó todavía), después por prioridad ALTA, MEDIA, BAJA, después las
     * que esperan triage; dentro de cada grupo, la más antigua primero.
     *
     * @param estado null para todas
     */
    List<ItemDeBandeja> bandeja(EstadoQueja estado, int pagina, int tamanio);

    /**
     * @param triageEstado SUGERIDO, MANUAL o PENDIENTE (todavía sin triage)
     */
    record ItemDeBandeja(UUID id, String empresa, String triageEstado, String categoriaSugerida, String resumen,
            String prioridad, List<String> articulosSugeridos, EstadoQueja estado, Instant createdAt) {
    }
}
