package ar.libroabierto.triage.application;

public class LlmNoDisponibleException extends RuntimeException {

    public LlmNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
