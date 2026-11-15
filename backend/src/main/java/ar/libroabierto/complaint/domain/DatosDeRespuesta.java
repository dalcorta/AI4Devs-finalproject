package ar.libroabierto.complaint.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Lo que se publica de la respuesta de una empresa (HU-05). La queja se identifica por su id y su hash on-chain, así el
 * documento queda atado a la queja que responde; el autor (un usuario interno) no forma parte.
 */
public record DatosDeRespuesta(long quejaOnchainId, String quejaContentHash, Empresa empresa, String texto,
        Instant respondidaEn) {

    public DatosDeRespuesta {
        Objects.requireNonNull(quejaContentHash);
        Objects.requireNonNull(empresa);
        Objects.requireNonNull(texto);
        Objects.requireNonNull(respondidaEn);
    }
}
