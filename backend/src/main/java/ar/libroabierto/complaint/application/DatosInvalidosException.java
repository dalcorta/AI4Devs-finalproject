package ar.libroabierto.complaint.application;

import java.util.Map;

/** Uno o más campos inválidos, con el motivo de cada uno, para mostrarlos todos juntos en el formulario. */
public class DatosInvalidosException extends RuntimeException {

    private final Map<String, String> errores;

    public DatosInvalidosException(Map<String, String> errores) {
        super("Datos inválidos: " + errores.keySet());
        this.errores = Map.copyOf(errores);
    }

    public Map<String, String> errores() {
        return errores;
    }
}
