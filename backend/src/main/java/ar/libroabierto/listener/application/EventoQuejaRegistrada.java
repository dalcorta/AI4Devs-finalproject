package ar.libroabierto.listener.application;

/** Evento {@code ComplaintFiled} del contrato, con el bloque y la transacción que lo emitieron. */
public record EventoQuejaRegistrada(long onchainId, long companyCuit, String contentHash, String ipfsCid, long bloque,
        String txHash) {
}
