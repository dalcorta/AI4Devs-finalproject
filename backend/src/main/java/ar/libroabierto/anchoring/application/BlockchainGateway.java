package ar.libroabierto.anchoring.application;

public interface BlockchainGateway {

    /** Envía {@code fileComplaint} al contrato y devuelve el hash de la transacción. */
    String registrarQueja(long companyCuit, String contentHash, String ipfsCid);
}
