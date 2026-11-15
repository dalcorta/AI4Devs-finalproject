package ar.libroabierto.listener.application;

/** Evento {@code ResponseFiled} del contrato, con el bloque y la transacción que lo emitieron. */
public record EventoRespuestaRegistrada(long quejaOnchainId, String contentHash, String ipfsCid, long bloque,
        String txHash) {
}
