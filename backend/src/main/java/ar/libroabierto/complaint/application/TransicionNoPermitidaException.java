package ar.libroabierto.complaint.application;

public class TransicionNoPermitidaException extends RuntimeException {

    public TransicionNoPermitidaException(String motivo) {
        super(motivo);
    }
}
