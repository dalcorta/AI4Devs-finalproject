package ar.libroabierto.listener.application;

import ar.libroabierto.complaint.domain.EstadoQueja;

/** Evento {@code StatusChanged} del contrato, con el bloque y la transacción que lo emitieron. */
public record EventoEstadoCambiado(long onchainId, EstadoQueja anterior, EstadoQueja nuevo, long bloque,
        String txHash) {
}
