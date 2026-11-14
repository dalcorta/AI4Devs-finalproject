package ar.libroabierto.complaint.application;

import java.util.UUID;

public class QuejaNoEncontradaException extends RuntimeException {

    public QuejaNoEncontradaException(UUID id) {
        super("No existe la queja " + id);
    }
}
