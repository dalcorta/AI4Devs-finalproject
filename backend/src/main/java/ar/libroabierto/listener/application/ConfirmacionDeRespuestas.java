package ar.libroabierto.listener.application;

public interface ConfirmacionDeRespuestas {

    /**
     * Marca CONFIRMADA la respuesta con ese content_hash y su transacción, con el bloque. Es idempotente: reprocesar
     * el mismo evento no cambia nada.
     */
    void confirmar(EventoRespuestaRegistrada evento);
}
