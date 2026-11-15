package ar.libroabierto.complaint.application;

/** La queja todavía no se puede responder (no está anclada) o ya tiene su respuesta. */
public class RespuestaNoDisponibleException extends RuntimeException {

    public RespuestaNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
