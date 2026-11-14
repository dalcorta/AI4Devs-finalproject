package ar.libroabierto.complaint.domain;

import java.util.Arrays;
import java.util.List;

/**
 * Estado de la queja: la misma máquina de estados que {@code ComplaintRegistry.sol}, y en el mismo orden (el adaptador
 * manda el ordinal como {@code uint8}). REGISTRADA → EN_REVISION → DERIVADA → RESUELTA | DESESTIMADA.
 */
public enum EstadoQueja {
    REGISTRADA, EN_REVISION, DERIVADA, RESUELTA, DESESTIMADA;

    public boolean puedePasarA(EstadoQueja nuevo) {
        return switch (this) {
            case REGISTRADA -> nuevo == EN_REVISION;
            case EN_REVISION -> nuevo == DERIVADA;
            case DERIVADA -> nuevo == RESUELTA || nuevo == DESESTIMADA;
            case RESUELTA, DESESTIMADA -> false;
        };
    }

    public List<EstadoQueja> siguientes() {
        return Arrays.stream(values()).filter(this::puedePasarA).toList();
    }
}
