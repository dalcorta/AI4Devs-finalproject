package ar.libroabierto.privacidad.application;

/** El modelo local no respondió o respondió algo inutilizable. El mensaje nunca incluye el texto revisado. */
public class ModeloLocalNoDisponibleException extends RuntimeException {

    public ModeloLocalNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
