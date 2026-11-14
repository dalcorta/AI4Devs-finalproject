package ar.libroabierto.complaint.application;

import ar.libroabierto.complaint.domain.EstadoQueja;
import java.util.UUID;

public record CambioPendiente(UUID cambioId, UUID quejaId, long onchainId, EstadoQueja nuevo, String comentario) {
}
