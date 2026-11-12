package ar.libroabierto.triage.application;

/** La salida del LLM no cumple el esquema: se descarta y se reintenta. */
public class SalidaInvalidaException extends RuntimeException {

    public SalidaInvalidaException(String motivo) {
        super(motivo);
    }
}
