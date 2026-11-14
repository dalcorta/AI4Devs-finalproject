package ar.libroabierto.anchoring.application;

import ar.libroabierto.complaint.domain.EstadoQueja;

/** Envía {@code changeStatus} firmado con la clave institucional del funcionario (OFFICER_ROLE). */
public interface AnclajeDeEstados {

    /**
     * @param noteHash keccak256 del comentario, o cero si no hay
     * @return el hash de la transacción; si el contrato ya está en ese estado, el de la transacción que lo dejó así
     */
    String cambiarEstado(long onchainId, EstadoQueja nuevo, String noteHash);
}
