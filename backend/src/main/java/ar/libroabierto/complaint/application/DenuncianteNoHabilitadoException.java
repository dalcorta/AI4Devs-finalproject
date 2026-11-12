package ar.libroabierto.complaint.application;

public class DenuncianteNoHabilitadoException extends RuntimeException {

    public DenuncianteNoHabilitadoException() {
        super("Solo un ciudadano con identidad verificada puede registrar quejas");
    }
}
