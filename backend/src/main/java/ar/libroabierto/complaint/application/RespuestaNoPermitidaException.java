package ar.libroabierto.complaint.application;

/** El usuario no representa a la empresa denunciada en la queja. */
public class RespuestaNoPermitidaException extends RuntimeException {

    public RespuestaNoPermitidaException() {
        super("Solo la empresa denunciada puede responder esta queja");
    }
}
