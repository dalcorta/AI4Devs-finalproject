package ar.libroabierto.anchoring.application;

public interface AnclajeDeRespuestas {

    /** Envía {@code fileResponse} al contrato y devuelve el hash de la transacción. */
    String registrarRespuesta(long quejaOnchainId, String contentHash, String ipfsCid);
}
