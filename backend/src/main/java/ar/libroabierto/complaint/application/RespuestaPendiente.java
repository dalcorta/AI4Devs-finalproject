package ar.libroabierto.complaint.application;

import ar.libroabierto.complaint.domain.DatosDeRespuesta;
import java.util.UUID;

/** Respuesta por anclar; {@code ipfsCid} y {@code contentHash} son nulos hasta que se publica en IPFS. */
public record RespuestaPendiente(UUID id, DatosDeRespuesta datos, String ipfsCid, String contentHash) {

    public boolean estaPublicada() {
        return contentHash != null;
    }
}
